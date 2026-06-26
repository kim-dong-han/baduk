package com.example.badukanalyzer.service;

import com.example.badukanalyzer.domain.Move;
import com.example.badukanalyzer.dto.MoveDetail;
import com.example.badukanalyzer.dto.SingleGameResult;
import com.example.badukanalyzer.parser.GibParser;
import com.example.badukanalyzer.parser.SgfParser;
import com.example.badukanalyzer.util.CoordinateConverter;
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
import java.util.stream.Collectors;

@Service
public class SingleGameService {

    private final KataGoService kataGoService;
    private final AnalysisJobStore jobStore;
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

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
            SingleGameResult result = analyze(fileName);
            jobStore.put(jobId, AnalysisJobStore.Job.done(result.getId()));
        } catch (Exception e) {
            jobStore.put(jobId, AnalysisJobStore.Job.error(e.getMessage()));
        }
    }

    public SingleGameResult analyze(String fileName) throws Exception {
        String filePath = recordDir + "/" + fileName;
        List<Move> moves = parseFile(filePath);

        System.out.println("[SingleGame] 분석 시작: " + fileName + " (" + moves.size() + "수)");
        List<JsonNode> nodes = kataGoService.analyzeAllMoves(moves);
        System.out.println("[SingleGame] KataGo 결과 수신: " + nodes.size() + "개 노드");

        List<MoveDetail> moveDetails = buildMoveDetails(moves, nodes);

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

        SingleGameResult result = SingleGameResult.builder()
                .id(id)
                .fileName(fileName)
                .analyzedAt(now)
                .totalMoves(moves.size())
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
        return results;
    }

    public SingleGameResult getResult(String id) throws Exception {
        File file = new File(resultDir + "/" + id + ".json");
        return objectMapper.readValue(file, SingleGameResult.class);
    }

    public List<String> listGameFiles() {
        File dir = new File(recordDir);
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return !lower.startsWith("pro_") && (lower.endsWith(".gib") || lower.endsWith(".sgf"));
        });
        if (files == null) return List.of();
        return Arrays.stream(files).map(File::getName).sorted().collect(Collectors.toList());
    }

    public List<String> listProGameFiles() {
        File dir = new File(recordDir);
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.startsWith("pro_") && (lower.endsWith(".gib") || lower.endsWith(".sgf"));
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

    public Map<String, String> getResultMap() throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        for (SingleGameResult r : listResults()) {
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

    private List<MoveDetail> buildMoveDetails(List<Move> moves, List<JsonNode> nodes) {
        Map<Integer, JsonNode> byTurn = new HashMap<>();
        for (JsonNode node : nodes) {
            byTurn.put(node.get("turnNumber").asInt(), node);
        }

        List<MoveDetail> details = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            JsonNode before = byTurn.get(i);
            JsonNode after  = byTurn.get(i + 1);
            if (before == null || after == null) continue;

            double scoreLeadBefore = before.path("rootInfo").path("scoreLead").asDouble();
            double scoreLeadAfter  = after.path("rootInfo").path("scoreLead").asDouble();
            double winrateBefore   = before.path("rootInfo").path("winrate").asDouble();
            double winrateAfter    = after.path("rootInfo").path("winrate").asDouble();

            boolean isBlack = "B".equals(move.getColor());
            double winrateLoss = isBlack ? winrateBefore - winrateAfter : winrateAfter - winrateBefore;

            String actualGtp = CoordinateConverter.toGtpCoord(move);
            String bestMove = "";
            JsonNode moveInfos = before.path("moveInfos");

            // scoreLoss: rootInfo.scoreLead(최적 기대값)과 실제 착점의 scoreLead 비교
            // rootInfo.scoreLead = 현재 플레이어가 최선으로 뒀을 때 기대 집수 (Black 기준)
            // max(0,...) 클램핑: 노이즈로 인한 음수(득점) 방지
            double scoreLoss;
            if (moveInfos.isArray() && moveInfos.size() > 0) {
                bestMove = moveInfos.get(0).path("move").asText();
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
            details.add(MoveDetail.builder()
                    .turnNumber(turnNumber)
                    .color(move.getColor())
                    .move(actualGtp)
                    .bestMove(bestMove)
                    .matchesBest(actualGtp.equalsIgnoreCase(bestMove))
                    .winrateBefore(round3(winrateBefore))
                    .winrateAfter(round3(winrateAfter))
                    .winrateLoss(round3(winrateLoss))
                    .scoreLeadBefore(round2(scoreLeadBefore))
                    .scoreLeadAfter(round2(scoreLeadAfter))
                    .scoreLoss(round2(scoreLoss))
                    .grade(calcGrade(scoreLoss))
                    .phase(calcPhase(turnNumber))
                    .build());
        }
        return details;
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
        System.out.println("[SingleGame] 결과 저장: " + result.getId() + ".json");
    }

    private String calcGrade(double scoreLoss) {
        if (scoreLoss < 0.5) return "S";
        if (scoreLoss < 1.5) return "A";
        if (scoreLoss < 3.0) return "B";
        if (scoreLoss < 5.0) return "C";
        return "D";
    }

    private String calcPhase(int turnNumber) {
        if (turnNumber <= 50)  return "초반";
        if (turnNumber <= 150) return "중반";
        return "종반";
    }

    private double round2(double v) { return Math.round(v * 100)  / 100.0; }
    private double round3(double v) { return Math.round(v * 1000) / 1000.0; }
}
