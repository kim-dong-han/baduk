package com.example.badukanalyzer.service;

import com.example.badukanalyzer.domain.Move;
import com.example.badukanalyzer.util.CoordinateConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

@Service
public class KataGoService {

    @Value("${katago.path}")
    private String kataGoPath;

    @Value("${katago.model}")
    private String modelPath;

    @Value("${katago.config}")
    private String configPath;

    @Value("${katago.analysis-visits:1000}")
    private int analysisVisits;

    @Value("${katago.deep-visits:0}")
    private int deepVisits;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 단일 기보 전수 분석에 쓰는 visits (분석 메타 패널 표기용). */
    /** 실시간 분석판(analyzeTop)에 쓰는 수당 탐색 횟수. 화면 표시에도 쓰인다. */
    public static final int TOP_VISITS = 500;
    /** 분석 쿼리에 넣는 덤(집). 화면 표시에도 쓰인다. */
    public static final double KOMI = 6.5;

    public int getTopVisits() { return TOP_VISITS; }
    public double getKomi() { return KOMI; }

    public int getAnalysisVisits() { return analysisVisits; }

    /** 2차 정밀 분석 visits (실수·악수 국면 재분석용). */
    public int getDeepVisits() { return deepVisits; }

    /** 모델 파일명에서 네트워크 이름만 뽑아 반환. 짧은 블록 우선(예: b28c512nbt), 없으면 파일명. */
    public String getNetName() {
        if (modelPath == null || modelPath.isBlank()) return "unknown";
        String f = new java.io.File(modelPath).getName()
                .replaceFirst("\\.bin\\.gz$", "").replaceFirst("\\.txt\\.gz$", "");
        for (String tok : f.split("-")) {
            if (tok.matches("b\\d+c\\d+.*")) return tok;   // bNNcNN(nbt) 블록만
        }
        return f;
    }

    // 실시간 대국용 영구 프로세스
    private Process      playProcess = null;
    private BufferedWriter playWriter = null;
    private BufferedReader playReader = null;

    public record HybridGameResult(List<JsonNode> winrateNodes, List<JsonNode> qualityNodes) {}

    /**
     * 하이브리드 분석: 한 KataGo 세션에서 두 종류 쿼리를 전송
     *  - 매 수, visits=1  → 형세 변동 (winrateNodes)
     *  - 10수마다, visits=30 → 일치율 (qualityNodes)
     */
    public List<HybridGameResult> analyzeMultipleGamesHybrid(List<List<Move>> allGames) throws IOException {
        List<String> gameIds = new ArrayList<>();
        int totalAnalyzeTurns = 0;

        System.out.println("KataGo 하이브리드 분석 시작 (" + allGames.size() + "개 기보)");
        ProcessBuilder pb = new ProcessBuilder(kataGoPath, "analysis", "-model", modelPath, "-config", configPath);
        pb.redirectErrorStream(true);
        // 작업디렉터리=엔진 exe 폴더 → 백엔드 DLL(TensorRT/CUDA: nvinfer·cudnn·cublas)과
        // TRT 타이밍 캐시(KataGoData/trtcache)를 찾게 함. (OpenCL 빌드에도 무해)
        java.io.File exeDir = new java.io.File(kataGoPath).getParentFile();
        if (exeDir != null) pb.directory(exeDir);
        Process process = pb.start();

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        for (int gi = 0; gi < allGames.size(); gi++) {
            List<Move> moves = allGames.get(gi);
            String gameId = UUID.randomUUID().toString();
            gameIds.add(gameId);

            int totalMoves = moves.size();

            // 공통 moves 배열
            ArrayNode movesArray = objectMapper.createArrayNode();
            for (Move move : moves) {
                ArrayNode entry = movesArray.addArray();
                entry.add(move.getColor());
                entry.add(CoordinateConverter.toGtpCoord(move));
            }

            // 쿼리 1: 매 수, visits=1 (형세 변동용)
            List<Integer> allTurns = new ArrayList<>();
            for (int t = 0; t <= totalMoves; t++) allTurns.add(t);
            writer.write(buildQuery("wr_" + gameId, movesArray, allTurns, 1).toString());
            writer.newLine();
            totalAnalyzeTurns += allTurns.size();

            // 쿼리 2: 10수마다, visits=10 (일치율용)
            List<Integer> checkTurns = new ArrayList<>();
            for (int t = 0; t <= totalMoves; t += 10) checkTurns.add(t);
            if (!checkTurns.contains(totalMoves)) checkTurns.add(totalMoves);
            writer.write(buildQuery("q_" + gameId, movesArray, checkTurns, 30).toString());
            writer.newLine();
            totalAnalyzeTurns += checkTurns.size();

            System.out.println("  [" + (gi + 1) + "/" + allGames.size() + "] 쿼리 전송 (" + totalMoves + "수, wr:" + allTurns.size() + " q:" + checkTurns.size() + "): " + gameId);
        }
        writer.flush();
        writer.close();

        System.out.println("전체 쿼리 전송 완료, 결과 수신 대기 중...");
        List<JsonNode> allJsonLines = collectResults(process);

        int timeoutSeconds = Math.max(60, totalAnalyzeTurns * 2 + 30);
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("KataGo 타임아웃 (" + timeoutSeconds + "초)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("KataGo 인터럽트", e);
        }

        System.out.println("KataGo 결과 수신 완료 (" + allJsonLines.size() + "개 JSON 라인)");

        List<HybridGameResult> results = new ArrayList<>();
        for (String gid : gameIds) {
            List<JsonNode> wrNodes = new ArrayList<>();
            List<JsonNode> qNodes  = new ArrayList<>();
            String wrId = "wr_" + gid, qId = "q_" + gid;
            for (JsonNode node : allJsonLines) {
                if (!node.has("id")) continue;
                String nodeId = node.get("id").asText();
                if (wrId.equals(nodeId))     wrNodes.add(node);
                else if (qId.equals(nodeId)) qNodes.add(node);
            }
            results.add(new HybridGameResult(wrNodes, qNodes));
        }
        return results;
    }

    private ObjectNode buildQuery(String id, ArrayNode movesArray, List<Integer> analyzeTurns, int maxVisits) {
        return buildQuery(id, movesArray, analyzeTurns, maxVisits, false);
    }

    private ObjectNode buildQuery(String id, ArrayNode movesArray, List<Integer> analyzeTurns, int maxVisits, boolean includeOwnership) {
        ObjectNode query = objectMapper.createObjectNode();
        query.put("id", id);
        query.put("boardXSize", 19);
        query.put("boardYSize", 19);
        query.put("rules", "korean");   // 타이젬=한국식(집내기, 코미 6.5)
        query.put("komi", KOMI);
        query.set("moves", movesArray.deepCopy());
        query.set("analyzeTurns", objectMapper.valueToTree(analyzeTurns));
        query.put("maxVisits", maxVisits);
        if (includeOwnership) query.put("includeOwnership", true);  // 집(영역) 예측 361칸 반환
        return query;
    }

    private List<JsonNode> collectResults(Process process) throws IOException {
        List<JsonNode> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("{")) {
                    try { lines.add(objectMapper.readTree(line)); }
                    catch (Exception e) { System.err.println("JSON 파싱 에러: " + line); }
                } else {
                    System.out.println("KataGo: " + line);
                }
            }
        }
        return lines;
    }

    public List<List<JsonNode>> analyzeMultipleGames(List<List<Move>> allGames) throws IOException {
        List<HybridGameResult> hybrid = analyzeMultipleGamesHybrid(allGames);
        List<List<JsonNode>> result = new ArrayList<>();
        for (HybridGameResult h : hybrid) result.add(h.winrateNodes());
        return result;
    }

    public List<JsonNode> analyzeMoves(List<Move> moves) throws IOException {
        return analyzeMultipleGames(List.of(moves)).getFirst();
    }

    // 단일 기보 전수 분석 - 진행률 콜백으로 실시간 % 보고
    public List<JsonNode> analyzeAllMoves(List<Move> moves, IntConsumer progressCallback) throws IOException {
        List<Integer> allTurns = new ArrayList<>();
        for (int t = 0; t <= moves.size(); t++) allTurns.add(t);
        return runTurnAnalysis(moves, allTurns, analysisVisits, true, progressCallback);
    }

    public List<JsonNode> analyzeAllMoves(List<Move> moves) throws IOException {
        return analyzeAllMoves(moves, null);
    }

    /** 특정 턴들만 지정 visits로 재분석 (적응형 2차 정밀 분석용). turns 비면 빈 리스트. */
    public List<JsonNode> analyzeTurnsAt(List<Move> moves, java.util.Collection<Integer> turns,
                                         int maxVisits, boolean includeOwnership) throws IOException {
        if (turns == null || turns.isEmpty()) return List.of();
        List<Integer> turnList = new ArrayList<>(new java.util.TreeSet<>(turns));  // 정렬·중복제거
        return runTurnAnalysis(moves, turnList, maxVisits, includeOwnership, null);
    }

    /** 지정 턴 집합을 한 KataGo 세션에서 maxVisits로 분석해 turnNumber 순 정렬 반환. */
    private List<JsonNode> runTurnAnalysis(List<Move> moves, List<Integer> turns, int maxVisits,
                                           boolean includeOwnership, IntConsumer progressCallback) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(kataGoPath, "analysis", "-model", modelPath, "-config", configPath);
        pb.redirectErrorStream(true);
        // 작업디렉터리=엔진 exe 폴더 → 백엔드 DLL(TensorRT/CUDA: nvinfer·cudnn·cublas)과
        // TRT 타이밍 캐시(KataGoData/trtcache)를 찾게 함. (OpenCL 빌드에도 무해)
        java.io.File exeDir = new java.io.File(kataGoPath).getParentFile();
        if (exeDir != null) pb.directory(exeDir);
        Process process = pb.start();

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        ArrayNode movesArray = objectMapper.createArrayNode();
        for (Move move : moves) {
            ArrayNode entry = movesArray.addArray();
            entry.add(move.getColor());
            entry.add(CoordinateConverter.toGtpCoord(move));
        }

        int totalTurns = turns.size();
        String queryId = UUID.randomUUID().toString();
        writer.write(buildQuery(queryId, movesArray, turns, maxVisits, includeOwnership).toString());
        writer.newLine();
        writer.flush();
        writer.close();

        System.out.println("KataGo 분석 시작 (" + totalTurns + "턴, visits=" + maxVisits + ")");

        // 결과를 한 줄씩 받으면서 progress 갱신
        List<JsonNode> allLines = new ArrayList<>();
        int received = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("{")) {
                    try {
                        allLines.add(objectMapper.readTree(line));
                        received++;
                        if (progressCallback != null && totalTurns > 0) {
                            progressCallback.accept(Math.min(99, received * 100 / totalTurns));
                        }
                    } catch (Exception e) { System.err.println("JSON 파싱 에러: " + line); }
                } else {
                    System.out.println("KataGo: " + line);
                }
            }
        }

        // visits가 클수록 턴당 분석이 오래 걸리므로 타임아웃도 비례 확대
        int timeoutSeconds = Math.max(120, totalTurns * Math.max(4, maxVisits / 100) + 60);
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("KataGo 타임아웃 (" + timeoutSeconds + "초)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("KataGo 인터럽트", e);
        }

        return allLines.stream()
                .filter(n -> n.has("id") && queryId.equals(n.get("id").asText()))
                .filter(n -> n.has("turnNumber"))
                .sorted(java.util.Comparator.comparingInt(n -> n.get("turnNumber").asInt()))
                .collect(java.util.stream.Collectors.toList());
    }

    /** 실시간 대국용 영구 프로세스 초기화 (죽어 있으면 재시작) */
    private synchronized void ensurePlayProcess() throws IOException {
        if (playProcess != null && playProcess.isAlive()) return;
        ProcessBuilder pb = new ProcessBuilder(kataGoPath, "analysis", "-model", modelPath, "-config", configPath);
        pb.redirectErrorStream(true);
        // 작업디렉터리=엔진 exe 폴더 → 백엔드 DLL(TensorRT/CUDA: nvinfer·cudnn·cublas)과
        // TRT 타이밍 캐시(KataGoData/trtcache)를 찾게 함. (OpenCL 빌드에도 무해)
        java.io.File exeDir = new java.io.File(kataGoPath).getParentFile();
        if (exeDir != null) pb.directory(exeDir);
        playProcess = pb.start();
        playWriter  = new BufferedWriter(new OutputStreamWriter(playProcess.getOutputStream(), StandardCharsets.UTF_8));
        playReader  = new BufferedReader(new InputStreamReader(playProcess.getInputStream(),   StandardCharsets.UTF_8));
    }

    /** 현재 국면에서 KataGo 최선수 GTP 좌표 반환 (실시간 대국용, 영구 프로세스) */
    public synchronized String getBestMove(List<Move> moves) throws IOException {
        return getBestMoveEval(moves).move();
    }

    /** 최선수 + 현재 국면 rootInfo.scoreLead(흑 기준). 실시간 수 평가용. */
    public record MoveEval(String move, double rootScoreLead) {}

    public synchronized MoveEval getBestMoveEval(List<Move> moves) throws IOException {
        ensurePlayProcess();

        ArrayNode movesArray = objectMapper.createArrayNode();
        for (Move move : moves) {
            ArrayNode entry = movesArray.addArray();
            entry.add(move.getColor());
            entry.add(CoordinateConverter.toGtpCoord(move));
        }

        String queryId = "play_" + System.currentTimeMillis();
        ObjectNode query = buildQuery(queryId, movesArray, List.of(moves.size()), 500);
        playWriter.write(query.toString());
        playWriter.newLine();
        playWriter.flush();

        // 해당 queryId 응답만 읽기 (타임아웃 10초)
        long deadline = System.currentTimeMillis() + 10_000;
        String line;
        while (System.currentTimeMillis() < deadline) {
            if (!playReader.ready()) {
                try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                continue;
            }
            line = playReader.readLine();
            if (line == null) break;
            try {
                JsonNode node = objectMapper.readTree(line);
                if (queryId.equals(node.path("id").asText())) {
                    double lead = node.path("rootInfo").path("scoreLead").asDouble(0);
                    JsonNode mi = node.path("moveInfos");
                    String best = (mi.isArray() && mi.size() > 0) ? mi.get(0).path("move").asText("pass") : "pass";
                    return new MoveEval(best, lead);
                }
            } catch (Exception ignored) {}
        }
        return new MoveEval("pass", 0);
    }

    /** 추천 후보수 1개. winrate·scoreLead 는 KataGo 원시값 = **흑 기준**(실측 확인: 백 차례·흑 완승도 winrate=1.0).
     *  둘 쪽/내 관점이 필요하면 호출부에서 색에 따라 (백이면 1-winrate, -scoreLead) 변환할 것.
     *  pv = 이 수 이후 예상 진행(참고도) GTP 수순, pv.get(0)=이 수 자신, 이후 상대·나 교대(최대 10수). */
    public record Candidate(String move, double winrate, double scoreLead, int visits, java.util.List<String> pv) {}

    /**
     * 현재 국면의 상위 topN 추천수(둘 차례 관점 승률 포함). getBestMoveEval 과 동일한 단일 쿼리라
     * 후보를 1개 읽든 5개 읽든 분석 시간은 같다(느려지는 건 visits, 후보 개수가 아님).
     */
    public synchronized List<Candidate> getTopMoves(List<Move> moves, int topN) throws IOException {
        ensurePlayProcess();

        ArrayNode movesArray = objectMapper.createArrayNode();
        for (Move move : moves) {
            ArrayNode entry = movesArray.addArray();
            entry.add(move.getColor());
            entry.add(CoordinateConverter.toGtpCoord(move));
        }

        String queryId = "playtop_" + System.currentTimeMillis();
        ObjectNode query = buildQuery(queryId, movesArray, List.of(moves.size()), 500);
        playWriter.write(query.toString());
        playWriter.newLine();
        playWriter.flush();

        long deadline = System.currentTimeMillis() + 10_000;
        String line;
        while (System.currentTimeMillis() < deadline) {
            if (!playReader.ready()) {
                try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                continue;
            }
            line = playReader.readLine();
            if (line == null) break;
            try {
                JsonNode node = objectMapper.readTree(line);
                if (queryId.equals(node.path("id").asText())) {
                    JsonNode mi = node.path("moveInfos");
                    List<JsonNode> list = new ArrayList<>();
                    if (mi.isArray()) mi.forEach(list::add);
                    list.sort(Comparator.comparingInt(a -> a.path("order").asInt(Integer.MAX_VALUE)));
                    List<Candidate> out = new ArrayList<>();
                    for (JsonNode c : list) {
                        if (out.size() >= topN) break;
                        List<String> pv = new ArrayList<>();
                        JsonNode pvNode = c.path("pv");
                        if (pvNode.isArray()) for (JsonNode p : pvNode) { pv.add(p.asText()); if (pv.size() >= 10) break; }
                        out.add(new Candidate(
                            c.path("move").asText("pass"),
                            c.path("winrate").asDouble(0.5),
                            c.path("scoreLead").asDouble(0),
                            c.path("visits").asInt(0),
                            pv));
                    }
                    return out;
                }
            } catch (Exception ignored) {}
        }
        return List.of();
    }

    /** 임의 국면 분석 결과: 루트 승률·집차 + 상위 후보수 + 집(영역) 예측 361칸(흑 기준 +흑/−백).
     *  winrate/scoreLead 모두 KataGo 원시 **흑 기준**(변환은 호출부). */
    public record TopResult(double rootWinrate, double rootScoreLead, List<Candidate> candidates, java.util.List<Double> ownership) {}

    /**
     * 임의 국면(moves 끝이 방금 둔 수)의 '둘 차례' 관점 승률·집차와 상위 topN 추천수를 함께 반환.
     * getTopMoves 와 동일한 단일 500-visits 쿼리 — 후보를 몇 개 읽든 시간은 같다. 놓아보기(결과화면)용.
     */
    public synchronized TopResult analyzeTop(List<Move> moves, int topN) throws IOException {
        ensurePlayProcess();

        ArrayNode movesArray = objectMapper.createArrayNode();
        for (Move move : moves) {
            ArrayNode entry = movesArray.addArray();
            entry.add(move.getColor());
            entry.add(CoordinateConverter.toGtpCoord(move));
        }

        String queryId = "trytop_" + System.currentTimeMillis();
        ObjectNode query = buildQuery(queryId, movesArray, List.of(moves.size()), TOP_VISITS, true);  // 집(영역) 예측 포함
        playWriter.write(query.toString());
        playWriter.newLine();
        playWriter.flush();

        long deadline = System.currentTimeMillis() + 10_000;
        String line;
        while (System.currentTimeMillis() < deadline) {
            if (!playReader.ready()) {
                try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                continue;
            }
            line = playReader.readLine();
            if (line == null) break;
            try {
                JsonNode node = objectMapper.readTree(line);
                if (queryId.equals(node.path("id").asText())) {
                    JsonNode root = node.path("rootInfo");
                    double rootWr    = root.path("winrate").asDouble(0.5);
                    double rootScore = root.path("scoreLead").asDouble(0);
                    JsonNode mi = node.path("moveInfos");
                    List<JsonNode> list = new ArrayList<>();
                    if (mi.isArray()) mi.forEach(list::add);
                    list.sort(Comparator.comparingInt(a -> a.path("order").asInt(Integer.MAX_VALUE)));
                    List<Candidate> out = new ArrayList<>();
                    for (JsonNode c : list) {
                        if (out.size() >= topN) break;
                        List<String> pv = new ArrayList<>();
                        JsonNode pvNode = c.path("pv");
                        if (pvNode.isArray()) for (JsonNode p : pvNode) { pv.add(p.asText()); if (pv.size() >= 10) break; }
                        out.add(new Candidate(
                            c.path("move").asText("pass"),
                            c.path("winrate").asDouble(0.5),
                            c.path("scoreLead").asDouble(0),
                            c.path("visits").asInt(0),
                            pv));
                    }
                    List<Double> own = new ArrayList<>();
                    JsonNode ownNode = node.path("ownership");
                    if (ownNode.isArray()) for (JsonNode v : ownNode) own.add(v.asDouble());
                    return new TopResult(rootWr, rootScore, out, own);
                }
            } catch (Exception ignored) {}
        }
        return new TopResult(0.5, 0, List.of(), List.of());
    }

    /** 형세 판단/계가용: 현재 국면의 흑 기준 집차(scoreLead)·흑 승률·집 영역(ownership 361칸). */
    public record PositionEval(double scoreLead, double winrate, java.util.List<Double> ownership) {}

    public synchronized PositionEval evaluatePosition(List<Move> moves) throws IOException {
        ensurePlayProcess();

        ArrayNode movesArray = objectMapper.createArrayNode();
        for (Move move : moves) {
            ArrayNode entry = movesArray.addArray();
            entry.add(move.getColor());
            entry.add(CoordinateConverter.toGtpCoord(move));
        }

        String queryId = "est_" + System.currentTimeMillis();
        ObjectNode query = buildQuery(queryId, movesArray, List.of(moves.size()), 300, true);  // 집 영역 포함
        playWriter.write(query.toString());
        playWriter.newLine();
        playWriter.flush();

        long deadline = System.currentTimeMillis() + 15_000;
        String line;
        while (System.currentTimeMillis() < deadline) {
            if (!playReader.ready()) {
                try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                continue;
            }
            line = playReader.readLine();
            if (line == null) break;
            try {
                JsonNode node = objectMapper.readTree(line);
                if (queryId.equals(node.path("id").asText())) {
                    double lead = node.path("rootInfo").path("scoreLead").asDouble(0);
                    double wr   = node.path("rootInfo").path("winrate").asDouble(0.5);
                    List<Double> own = new ArrayList<>();
                    JsonNode ownNode = node.path("ownership");
                    if (ownNode.isArray()) for (JsonNode v : ownNode) own.add(v.asDouble());
                    return new PositionEval(lead, wr, own);
                }
            } catch (Exception ignored) {}
        }
        return new PositionEval(0, 0.5, java.util.List.of());
    }
}
