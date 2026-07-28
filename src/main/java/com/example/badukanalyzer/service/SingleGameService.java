package com.example.badukanalyzer.service;

import com.example.badukanalyzer.domain.Move;
import com.example.badukanalyzer.dto.MoveDetail;
import com.example.badukanalyzer.dto.SingleGameResult;
import com.example.badukanalyzer.parser.GibParser;
import com.example.badukanalyzer.parser.SgfParser;
import com.example.badukanalyzer.util.CoordinateConverter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

@Service
public class SingleGameService {

    private final KataGoService kataGoService;
    private final AnalysisJobStore jobStore;
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** 목록·집계 전용 경량 파서: 수당 무거운 필드(ownership·candidates·bestPv·topMoves)는 건너뛴다.
     *  이 필드들은 상세 복기 페이지(getResult)에서만 쓰이므로 목록/리포트에는 불필요. */
    @JsonIgnoreProperties({"ownership", "candidates", "bestPv", "topMoves"})
    private abstract static class MoveDetailSummaryMixin {}
    private final ObjectMapper summaryMapper = new ObjectMapper()
            .addMixIn(MoveDetail.class, MoveDetailSummaryMixin.class)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // 목록 요약 캐시 (result-dir 상태 서명이 같으면 재파싱 생략)
    private volatile List<SingleGameResult> cachedSummaries;
    private volatile String cachedSig;

    @Value("${katago.record-dir}")
    private String recordDir;

    @Value("${katago.result-dir}")
    private String resultDir;

    public SingleGameService(KataGoService kataGoService, AnalysisJobStore jobStore) {
        this.kataGoService = kataGoService;
        this.jobStore = jobStore;
    }

    @Async
    public void analyzeAsync(String jobId, String fileName) {
        try {
            SingleGameResult result = analyze(fileName,
                    pct -> jobStore.updateProgress(jobId, pct));
            jobStore.put(jobId, AnalysisJobStore.Job.done(result.getId()));
        } catch (Exception e) {
            jobStore.put(jobId, AnalysisJobStore.Job.error(e.getMessage()));
        }
    }

    public SingleGameResult analyze(String fileName) throws Exception {
        return analyze(fileName, null);
    }

    private SingleGameResult analyze(String fileName, IntConsumer progressCallback) throws Exception {
        String filePath = recordDir + "/" + fileName;
        List<Move> moves = parseFile(filePath);

        System.out.println("[SingleGame] 분석 시작: " + fileName + " (" + moves.size() + "수)");
        long startedAt = System.currentTimeMillis();
        List<JsonNode> nodes = kataGoService.analyzeAllMoves(moves, progressCallback);
        System.out.println("[SingleGame] 1차 KataGo 결과 수신: " + nodes.size() + "개 노드");

        List<MoveDetail> moveDetails = buildMoveDetails(moves, nodes);

        // ── 2차 정밀 분석 ── 1차(저visits)에서 실수·악수(집손해≥3)로 잡힌 국면만 고visits로 재분석.
        // 저visits 오판(형세 착시)을 교정하면서, 전 수를 고visits로 돌리는 낭비는 피한다.
        int deepVisits = kataGoService.getDeepVisits();
        int deepMoveCount = 0;
        if (deepVisits > kataGoService.getAnalysisVisits()) {
            java.util.TreeSet<Integer> deepTurns = new java.util.TreeSet<>();
            for (MoveDetail d : moveDetails) {
                if (d.getScoreLoss() >= 3.0) {           // 실수(≥3)·악수(≥5)
                    int i = d.getTurnNumber() - 1;
                    deepTurns.add(i);                    // 착점 전 국면(후보·최선수·변화도·집손해 기준)
                    deepTurns.add(i + 1);                // 착점 후 국면(형세·집예측)
                }
            }
            if (!deepTurns.isEmpty()) {
                System.out.println("[SingleGame] 2차 정밀 분석: 실수·악수 국면 " + deepTurns.size() + "턴, visits=" + deepVisits);
                List<JsonNode> deepNodes = kataGoService.analyzeTurnsAt(moves, deepTurns, deepVisits, true);
                Map<Integer, JsonNode> byTurn = new HashMap<>();
                for (JsonNode n : nodes)     byTurn.put(n.get("turnNumber").asInt(), n);
                for (JsonNode n : deepNodes) byTurn.put(n.get("turnNumber").asInt(), n);  // 정밀분석이 덮어씀
                for (int idx = 0; idx < moveDetails.size(); idx++) {
                    if (moveDetails.get(idx).getScoreLoss() >= 3.0) {
                        int i = moveDetails.get(idx).getTurnNumber() - 1;
                        MoveDetail rebuilt = buildOneMoveDetail(moves, i, byTurn.get(i), byTurn.get(i + 1), true);
                        if (rebuilt != null) { moveDetails.set(idx, rebuilt); deepMoveCount++; }
                    }
                }
                System.out.println("[SingleGame] 2차 정밀 재분석 완료: " + deepMoveCount + "수 교정");
            }
        }

        long durationMs = System.currentTimeMillis() - startedAt;
        final int deepMoveCountFinal = deepMoveCount;
        final Integer deepVisitsMeta = deepMoveCount > 0 ? deepVisits : null;

        List<MoveDetail> top3Mistakes = moveDetails.stream()
                .filter(m -> m.getScoreLoss() > 0)
                .sorted(Comparator.comparingDouble(MoveDetail::getScoreLoss).reversed())
                .limit(3)
                .collect(Collectors.toList());

        List<MoveDetail> top3GoodMoves = moveDetails.stream()
                .filter(m -> m.getScoreLoss() < -2.0)
                .sorted(Comparator.comparingDouble(MoveDetail::getScoreLoss))
                .limit(3)
                .collect(Collectors.toList());

        String id = UUID.randomUUID().toString();
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String[] players = parsePlayers(filePath);

        SingleGameResult result = SingleGameResult.builder()
                .id(id)
                .fileName(fileName)
                .blackPlayer(players[0])
                .whitePlayer(players[1])
                .analyzedAt(now)
                .totalMoves(moves.size())
                .engineNet(kataGoService.getNetName())
                .analysisVisits(kataGoService.getAnalysisVisits())
                .deepVisits(deepVisitsMeta)
                .deepMoveCount(deepMoveCountFinal)
                .analysisDurationMs(durationMs)
                .moves(moveDetails)
                .top3Mistakes(top3Mistakes)
                .top3GoodMoves(top3GoodMoves)
                .opening(calcPhaseStats("초반", moveDetails.stream().filter(m -> "초반".equals(m.getPhase())).collect(Collectors.toList())))
                .middle(calcPhaseStats("중반", moveDetails.stream().filter(m -> "중반".equals(m.getPhase())).collect(Collectors.toList())))
                .endgame(calcPhaseStats("종반", moveDetails.stream().filter(m -> "종반".equals(m.getPhase())).collect(Collectors.toList())))
                .build();

        saveResult(result);
        return result;
    }

    public List<SingleGameResult> listResults() throws Exception {
        File dir = new File(resultDir);
        if (!dir.exists()) return List.of();

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return List.of();

        List<SingleGameResult> results = new ArrayList<>();
        for (File f : files) {
            try {
                results.add(objectMapper.readValue(f, SingleGameResult.class));
            } catch (Exception e) {
                System.err.println("결과 파일 읽기 실패: " + f.getName());
            }
        }
        results.sort(Comparator.comparing(SingleGameResult::getAnalyzedAt).reversed());

        // 같은 기보를 재분석하면 결과 JSON이 누적됨 → 파일명 기준 최신 분석만 남김
        // (정렬이 최신순이므로 putIfAbsent가 가장 최근 분석을 보존)
        return dedupeLatestByFile(results);
    }

    /**
     * 목록·집계용 결과 요약. listResults()와 같은 내용이되 수당 무거운 필드를 생략하고,
     * result-dir 상태가 그대로면 캐시를 재사용한다. 갤러리/실력리포트/오답노트/인덱스가 사용.
     */
    public List<SingleGameResult> listResultSummaries() {
        File dir = new File(resultDir);
        if (!dir.exists()) return List.of();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return List.of();

        String sig = dirSignature(files);
        List<SingleGameResult> cached = cachedSummaries;
        if (cached != null && sig.equals(cachedSig)) return cached;

        List<SingleGameResult> results = new ArrayList<>();
        for (File f : files) {
            try {
                results.add(summaryMapper.readValue(f, SingleGameResult.class));
            } catch (Exception e) {
                System.err.println("결과 요약 읽기 실패: " + f.getName());
            }
        }
        results.sort(Comparator.comparing(SingleGameResult::getAnalyzedAt).reversed());
        List<SingleGameResult> deduped = dedupeLatestByFile(results);

        cachedSummaries = deduped;
        cachedSig = sig;
        return deduped;
    }

    // 같은 기보 재분석분 누적 → 파일명 기준 최신 분석만 남김 (입력은 최신순 정렬 가정)
    private List<SingleGameResult> dedupeLatestByFile(List<SingleGameResult> results) {
        Map<String, SingleGameResult> latestByFile = new LinkedHashMap<>();
        for (SingleGameResult r : results) {
            latestByFile.putIfAbsent(r.getFileName(), r);
        }
        return new ArrayList<>(latestByFile.values());
    }

    // 파일 개수·이름·수정시각 기반 서명 — 새 분석/재저장/삭제 시 값이 바뀌어 캐시가 갱신됨
    private String dirSignature(File[] files) {
        long h = files.length;
        for (File f : files) h = h * 31 + f.getName().hashCode() * 1315423911L + f.lastModified();
        return files.length + ":" + h;
    }

    public SingleGameResult getResult(String id) throws Exception {
        File file = new File(resultDir + "/" + id + ".json");
        SingleGameResult result = objectMapper.readValue(file, SingleGameResult.class);

        // 대국자명 보정: 원본 SGF가 있으면 흑/백을 다시 읽어 저장값과 다르면 갱신·재저장.
        // (대국자명 필드 추가 이전 결과 채우기 + 외국 기사 깨진 이름의 파일명 보정 반영)
        if (result.getFileName() != null) {
            File src = new File(recordDir + "/" + result.getFileName());
            if (src.exists()) {
                String[] players = parsePlayers(src.getPath());
                boolean changed =
                        (players[0] != null && !players[0].equals(result.getBlackPlayer())) ||
                        (players[1] != null && !players[1].equals(result.getWhitePlayer()));
                if (changed) {
                    result.setBlackPlayer(players[0]);
                    result.setWhitePlayer(players[1]);
                    saveResult(result);
                }
            }
        }
        return result;
    }

    /** 분석 결과를 코멘트(수번·등급·집손해·최선수) 달린 SGF 문자열로 변환. 어떤 바둑 뷰어에서도 열림. */
    public String buildAnnotatedSgf(SingleGameResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("(;GM[1]FF[4]CA[UTF-8]SZ[19]AP[baduk-analyzer]");
        if (r.getBlackPlayer() != null) sb.append("PB[").append(escSgf(r.getBlackPlayer())).append("]");
        if (r.getWhitePlayer() != null) sb.append("PW[").append(escSgf(r.getWhitePlayer())).append("]");
        if (r.getMoves() != null) {
            for (MoveDetail m : r.getMoves()) {
                sb.append(";").append(m.getColor()).append("[");
                String gtp = m.getMove();
                if (gtp != null && !gtp.isEmpty() && !gtp.equalsIgnoreCase("pass")) {
                    Move mv = CoordinateConverter.fromGtp(m.getColor(), gtp);
                    sb.append(CoordinateConverter.toSgfX(mv.getX())).append(CoordinateConverter.toSgfY(mv.getY()));
                }
                sb.append("]");
                StringBuilder c = new StringBuilder();
                c.append(m.getTurnNumber()).append("수 · 등급 ").append(m.getGrade());
                if (m.getScoreLoss() > 0.05) c.append(" · 집손해 ").append(String.format("%.1f", m.getScoreLoss())).append("집");
                if (m.getBestMove() != null && !m.getBestMove().equalsIgnoreCase(gtp)) c.append(" · 최선수 ").append(m.getBestMove());
                sb.append("C[").append(escSgf(c.toString())).append("]");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private static String escSgf(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("]", "\\]");
    }

    public List<String> listGameFiles() {
        File dir = new File(recordDir);
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return !name.contains("신진서 vs") && (lower.endsWith(".gib") || lower.endsWith(".sgf"));
        });
        if (files == null) return List.of();
        return Arrays.stream(files).map(File::getName).sorted().collect(Collectors.toList());
    }

    public List<String> listProGameFiles() {
        File dir = new File(recordDir);
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return name.contains("신진서 vs") && (lower.endsWith(".gib") || lower.endsWith(".sgf"));
        });
        if (files == null) return List.of();
        return Arrays.stream(files).map(File::getName).sorted().collect(Collectors.toList());
    }

    public List<Map<String, String>> getRawMoves(String fileName) throws Exception {
        String filePath = recordDir + "/" + fileName;
        List<Move> moves = parseFile(filePath);
        List<Map<String, String>> result = new ArrayList<>();
        for (Move m : moves) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("color", m.getColor());
            entry.put("move", CoordinateConverter.toGtpCoord(m));
            result.add(entry);
        }
        return result;
    }

    public Map<String, String> getResultMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (SingleGameResult r : listResultSummaries()) {
            map.putIfAbsent(r.getFileName(), r.getId());
        }
        return map;
    }

    private List<Move> parseFile(String filePath) throws Exception {
        if (filePath.toLowerCase().endsWith(".gib")) {
            return new GibParser().parse(filePath);
        }
        return new SgfParser().parse(filePath);
    }

    // 흑/백 대국자명 추출 (현재 SGF 지원, gib는 null)
    private String[] parsePlayers(String filePath) {
        if (filePath.toLowerCase().endsWith(".sgf")) {
            return SgfParser.parsePlayers(filePath);
        }
        return new String[]{null, null};
    }

    private List<MoveDetail> buildMoveDetails(List<Move> moves, List<JsonNode> nodes) {
        Map<Integer, JsonNode> byTurn = new HashMap<>();
        for (JsonNode node : nodes) {
            byTurn.put(node.get("turnNumber").asInt(), node);
        }

        List<MoveDetail> details = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            MoveDetail d = buildOneMoveDetail(moves, i, byTurn.get(i), byTurn.get(i + 1), false);
            if (d != null) details.add(d);
        }
        return details;
    }

    /** 한 수(index i)의 MoveDetail 생성. before=turn i, after=turn i+1 노드. 어느 하나 null이면 null 반환. */
    private MoveDetail buildOneMoveDetail(List<Move> moves, int i, JsonNode before, JsonNode after, boolean deepAnalyzed) {
            Move move = moves.get(i);
            if (before == null || after == null) return null;

            double scoreLeadBefore = before.path("rootInfo").path("scoreLead").asDouble();
            double scoreLeadAfter  = after.path("rootInfo").path("scoreLead").asDouble();
            double winrateBefore   = before.path("rootInfo").path("winrate").asDouble();
            double winrateAfter    = after.path("rootInfo").path("winrate").asDouble();

            boolean isBlack = "B".equals(move.getColor());
            double winrateLoss = isBlack ? winrateBefore - winrateAfter : winrateAfter - winrateBefore;

            String actualGtp = CoordinateConverter.toGtpCoord(move);
            String bestMove = "";
            List<String> bestPv = new ArrayList<>();
            JsonNode moveInfos = before.path("moveInfos");

            // scoreLoss: rootInfo.scoreLead(최적 기대값)과 실제 착점의 scoreLead 비교
            // rootInfo.scoreLead = 현재 플레이어가 최선으로 뒀을 때 기대 집수 (Black 기준)
            // max(0,...) 클램핑: 노이즈로 인한 음수(득점) 방지
            List<String> topMoves = new ArrayList<>();
            List<MoveDetail.Candidate> candidates = new ArrayList<>();
            double scoreLoss;
            if (moveInfos.isArray() && moveInfos.size() > 0) {
                bestMove = moveInfos.get(0).path("move").asText();
                // 상위 3개 후보수: 좌표 + hover 상세(승률·집차·예상 진행)
                for (int j = 0; j < Math.min(3, moveInfos.size()); j++) {
                    JsonNode mi = moveInfos.get(j);
                    topMoves.add(mi.path("move").asText());
                    List<String> cpv = new ArrayList<>();
                    JsonNode cpvNode = mi.path("pv");
                    if (cpvNode.isArray()) {
                        for (JsonNode p : cpvNode) { cpv.add(p.asText()); if (cpv.size() >= 6) break; }
                    }
                    candidates.add(MoveDetail.Candidate.builder()
                            .move(mi.path("move").asText())
                            .winrate(round3(mi.path("winrate").asDouble()))
                            .scoreLead(round2(mi.path("scoreLead").asDouble()))
                            .pv(cpv)
                            .build());
                }
                // 최선수의 예상 진행(PV)을 변화도로 저장 (최대 8수)
                JsonNode pvNode = moveInfos.get(0).path("pv");
                if (pvNode.isArray()) {
                    for (JsonNode p : pvNode) {
                        bestPv.add(p.asText());
                        if (bestPv.size() >= 8) break;
                    }
                }
                double actualScoreLead = Double.NaN;
                for (JsonNode mi : moveInfos) {
                    if (actualGtp.equalsIgnoreCase(mi.path("move").asText())) {
                        actualScoreLead = mi.path("scoreLead").asDouble();
                        break;
                    }
                }
                if (!Double.isNaN(actualScoreLead)) {
                    // rootInfo.scoreLead = 이 국면에서 최선 플레이 기대값
                    double raw = isBlack ? scoreLeadBefore - actualScoreLead
                                        : actualScoreLead - scoreLeadBefore;
                    scoreLoss = Math.max(0, raw);
                } else {
                    // 실제 착점이 moveInfos에 없는 경우(매우 나쁜 수): 독립 비교 fallback (최소 0)
                    double raw = isBlack ? scoreLeadBefore - scoreLeadAfter
                                        : scoreLeadAfter - scoreLeadBefore;
                    scoreLoss = Math.max(0, raw);
                }
            } else {
                double raw = isBlack ? scoreLeadBefore - scoreLeadAfter
                                     : scoreLeadAfter - scoreLeadBefore;
                scoreLoss = Math.max(0, raw);
            }

            int turnNumber = i + 1;
            return MoveDetail.builder()
                    .turnNumber(turnNumber)
                    .color(move.getColor())
                    .move(actualGtp)
                    .bestMove(bestMove)
                    .topMoves(topMoves)
                    .bestPv(bestPv)
                    .matchesBest(actualGtp.equalsIgnoreCase(bestMove))
                    .winrateBefore(round3(winrateBefore))
                    .winrateAfter(round3(winrateAfter))
                    .winrateLoss(round3(winrateLoss))
                    .scoreLeadBefore(round2(scoreLeadBefore))
                    .scoreLeadAfter(round2(scoreLeadAfter))
                    .scoreLoss(round2(scoreLoss))
                    .grade(calcGrade(scoreLoss))
                    .phase(calcPhase(turnNumber))
                    .deepAnalyzed(deepAnalyzed)
                    .ownership(extractOwnership(after))  // 착점 후(turn i+1) 국면의 집 예측
                    .candidates(candidates)              // AI 후보수 상세 (hover용)
                    .build();
    }

    private SingleGameResult.PhaseStats calcPhaseStats(String phase, List<MoveDetail> phaseMoves) {
        if (phaseMoves.isEmpty()) {
            return SingleGameResult.PhaseStats.builder().phase(phase).moveCount(0).build();
        }
        double avgScoreLoss   = phaseMoves.stream().mapToDouble(MoveDetail::getScoreLoss).average().orElse(0);
        double avgWinrateLoss = phaseMoves.stream().mapToDouble(MoveDetail::getWinrateLoss).average().orElse(0);
        long matches  = phaseMoves.stream().filter(MoveDetail::isMatchesBest).count();
        long blunders = phaseMoves.stream().filter(m -> m.getScoreLoss() >= 5).count();
        long mistakes = phaseMoves.stream().filter(m -> m.getScoreLoss() >= 3 && m.getScoreLoss() < 5).count();

        return SingleGameResult.PhaseStats.builder()
                .phase(phase)
                .moveCount(phaseMoves.size())
                .avgScoreLoss(round2(avgScoreLoss))
                .avgWinrateLoss(round3(avgWinrateLoss))
                .matchRate(round2(matches * 100.0 / phaseMoves.size()))
                .blunderCount((int) blunders)
                .mistakeCount((int) mistakes)
                .build();
    }

    private void saveResult(SingleGameResult result) throws Exception {
        File dir = new File(resultDir);
        if (!dir.exists()) dir.mkdirs();
        objectMapper.writeValue(new File(resultDir + "/" + result.getId() + ".json"), result);
        cachedSummaries = null;   // 새 결과 → 요약 캐시 무효화
        System.out.println("[SingleGame] 결과 저장: " + result.getId() + ".json");
    }

    private String calcGrade(double scoreLoss) {
        if (scoreLoss < 0.5) return "최선";
        if (scoreLoss < 1.5) return "좋음";
        if (scoreLoss < 3.0) return "보통";
        if (scoreLoss < 5.0) return "실수";
        return "악수";
    }

    private String calcPhase(int turnNumber) {
        if (turnNumber <= 50)  return "초반";
        if (turnNumber <= 150) return "중반";
        return "종반";
    }

    // KataGo ownership 배열(361, y*19+x) → 2자리 반올림 List. 없으면 null(구 JSON/미요청)
    private List<Double> extractOwnership(JsonNode node) {
        JsonNode own = node.path("ownership");
        if (!own.isArray() || own.isEmpty()) return null;
        List<Double> list = new ArrayList<>(own.size());
        for (JsonNode v : own) list.add(round2(v.asDouble()));
        return list;
    }

    private double round2(double v) { return Math.round(v * 100)  / 100.0; }
    private double round3(double v) { return Math.round(v * 1000) / 1000.0; }
}
