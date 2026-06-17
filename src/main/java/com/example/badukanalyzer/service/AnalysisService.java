package com.example.badukanalyzer.service;

import com.example.badukanalyzer.domain.Move;
import com.example.badukanalyzer.dto.AnalysisResponse;
import com.example.badukanalyzer.parser.GibParser;
import com.example.badukanalyzer.parser.SgfParser;
import com.example.badukanalyzer.util.CoordinateConverter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnalysisService {

    private final KataGoService kataGoService;

    @Value("${katago.record-dir}")
    private String recordDir;

    private volatile List<AnalysisResponse> userResults;
    private volatile List<AnalysisResponse> proResults;
    private volatile String errorMessage;
    private volatile boolean running;

    public AnalysisService(KataGoService kataGoService) {
        this.kataGoService = kataGoService;
    }

    @PostConstruct
    public synchronized void startBackgroundAnalysis() {
        if (running) return;
        running = true;
        Thread.ofVirtual().name("batch-analysis").start(() -> {
            try {
                userResults = List.of(); // 내 기보 분석 임시 비활성화
                proResults = analyzeByPrefix("pro_", 50);
            } catch (Exception e) {
                errorMessage = e.getMessage();
            } finally {
                running = false;
            }
        });
    }

    public List<AnalysisResponse> getUserResults() { return userResults; }
    public List<AnalysisResponse> getProResults()  { return proResults; }
    public String getErrorMessage()                 { return errorMessage; }
    public boolean isRunning()                      { return running; }

    private List<AnalysisResponse> analyzeByPrefix(String prefix, int maxFiles) throws Exception {
        File dir = new File(recordDir);
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            boolean matchesPrefix = prefix.isEmpty() ? !lower.startsWith("pro_") : lower.startsWith(prefix);
            return matchesPrefix && (lower.endsWith(".gib") || lower.endsWith(".sgf"));
        });

        if (files == null || files.length == 0) {
            System.out.println((prefix.isEmpty() ? "내 기보" : "프로 기보") + " 파일 없음");
            return List.of();
        }

        GibParser gibParser = new GibParser();
        SgfParser sgfParser = new SgfParser();
        List<List<Move>> allMoves = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();

        int count = 0;
        for (File file : files) {
            if (count++ >= maxFiles) break;
            System.out.println("파싱: " + file.getName());
            String path = file.getAbsolutePath();
            allMoves.add(path.toLowerCase().endsWith(".gib") ? gibParser.parse(path) : sgfParser.parse(path));
            fileNames.add(file.getName());
        }
        if (allMoves.isEmpty()) return List.of();

        String label = prefix.isEmpty() ? "내 기보" : "프로 기보";
        System.out.println(label + " KataGo 하이브리드 분석 시작 (" + allMoves.size() + "개)");
        long t0 = System.currentTimeMillis();
        List<KataGoService.HybridGameResult> hybridResults = kataGoService.analyzeMultipleGamesHybrid(allMoves);
        System.out.println(label + " 분석 완료 (" + (System.currentTimeMillis() - t0) / 1000.0 + "초)");

        // 구간별 지표를 분리해서 수집
        List<WrMetric>      allOpeningWr = new ArrayList<>(), allMiddleWr = new ArrayList<>(), allEndgameWr = new ArrayList<>();
        List<QualityMetric> allOpeningQ  = new ArrayList<>(), allMiddleQ  = new ArrayList<>(), allEndgameQ  = new ArrayList<>();

        for (int i = 0; i < allMoves.size(); i++) {
            System.out.println("결과 처리: " + fileNames.get(i));
            collectWinrateMetrics(hybridResults.get(i).winrateNodes(), allOpeningWr, allMiddleWr, allEndgameWr);
            collectQualityMetrics(hybridResults.get(i).qualityNodes(), allMoves.get(i), allOpeningQ, allMiddleQ, allEndgameQ);
        }

        List<AnalysisResponse> result = new ArrayList<>();
        if (!allOpeningWr.isEmpty()) result.add(calculateSummary("초반", allOpeningWr, allOpeningQ));
        if (!allMiddleWr.isEmpty())  result.add(calculateSummary("중반", allMiddleWr,  allMiddleQ));
        if (!allEndgameWr.isEmpty()) result.add(calculateSummary("종반", allEndgameWr, allEndgameQ));
        return result;
    }

    // 매 수 winrate 수집 (형세 변동 전용)
    private void collectWinrateMetrics(List<JsonNode> nodes, List<WrMetric> opening, List<WrMetric> middle, List<WrMetric> endgame) {
        double prevWr = 0.5, prevScore = 0.0;
        List<JsonNode> sorted = nodes.stream()
                .filter(n -> n.has("rootInfo") && n.has("turnNumber"))
                .sorted(java.util.Comparator.comparingInt(n -> n.get("turnNumber").asInt()))
                .toList();

        for (JsonNode node : sorted) {
            int turn = node.get("turnNumber").asInt();
            double wr    = node.get("rootInfo").get("winrate").asDouble();
            double score = node.get("rootInfo").get("scoreLead").asDouble();

            WrMetric m = new WrMetric(Math.abs(wr - prevWr), Math.abs(score - prevScore));
            if      (turn <= 50)  opening.add(m);
            else if (turn <= 150) middle.add(m);
            else                  endgame.add(m);

            prevWr = wr; prevScore = score;
        }
    }

    // 10수마다 일치율 수집 (matchScore 전용)
    private void collectQualityMetrics(List<JsonNode> nodes, List<Move> actualMoves,
                                       List<QualityMetric> opening, List<QualityMetric> middle, List<QualityMetric> endgame) {
        for (JsonNode node : nodes) {
            if (!node.has("rootInfo") || !node.has("turnNumber")) continue;
            int turn = node.get("turnNumber").asInt();
            boolean isBlackTurn = (turn % 2 == 0);

            double matchScore = 0.6;
            if (turn < actualMoves.size() && node.has("moveInfos") && node.get("moveInfos").size() > 0) {
                String actualGtp = CoordinateConverter.toGtpCoord(actualMoves.get(turn));
                double topWr = node.get("moveInfos").get(0).get("winrate").asDouble();
                double actualWr = -1;
                for (JsonNode mi : node.get("moveInfos")) {
                    if (actualGtp.equalsIgnoreCase(mi.get("move").asText())) {
                        actualWr = mi.get("winrate").asDouble();
                        break;
                    }
                }
                if (actualWr >= 0) {
                    if (isBlackTurn) {
                        matchScore = topWr > 0 ? Math.min(1.0, actualWr / topWr) : 0.6;
                    } else {
                        double denom = 1.0 - topWr;
                        matchScore = denom > 0.01 ? Math.min(1.0, (1.0 - actualWr) / denom) : 0.6;
                    }
                }
            }

            QualityMetric m = new QualityMetric(matchScore);
            if      (turn <= 50)  opening.add(m);
            else if (turn <= 150) middle.add(m);
            else                  endgame.add(m);
        }
    }

    private AnalysisResponse calculateSummary(String phase, List<WrMetric> wrMetrics, List<QualityMetric> qMetrics) {
        double avgWrLoss    = wrMetrics.stream().mapToDouble(m -> m.wrLoss).average().orElse(0);
        double avgScoreLoss = wrMetrics.stream().mapToDouble(m -> m.scoreLoss).average().orElse(0);

        if (qMetrics.isEmpty()) {
            return AnalysisResponse.builder().phase(phase)
                    .matchRate(0).winRateLoss(round3(avgWrLoss)).scoreLoss(round2(avgScoreLoss))
                    .excellentRate(0).goodRate(0).normalRate(0).badRate(0).blunderRate(0).build();
        }

        int total = qMetrics.size();
        double avgMatch  = qMetrics.stream().mapToDouble(m -> m.matchScore).average().orElse(0);
        long excellent   = qMetrics.stream().filter(m -> m.matchScore >= 0.95).count();
        long good        = qMetrics.stream().filter(m -> m.matchScore >= 0.80 && m.matchScore < 0.95).count();
        long normal      = qMetrics.stream().filter(m -> m.matchScore >= 0.60 && m.matchScore < 0.80).count();
        long bad         = qMetrics.stream().filter(m -> m.matchScore >= 0.35 && m.matchScore < 0.60).count();
        long blunder     = qMetrics.stream().filter(m -> m.matchScore <  0.35).count();

        return AnalysisResponse.builder()
                .phase(phase)
                .matchRate(Math.round(avgMatch * 10000) / 100.0)
                .winRateLoss(round3(avgWrLoss))
                .scoreLoss(round2(avgScoreLoss))
                .excellentRate(Math.round(excellent * 10000.0 / total) / 100.0)
                .goodRate(Math.round(good      * 10000.0 / total) / 100.0)
                .normalRate(Math.round(normal   * 10000.0 / total) / 100.0)
                .badRate(Math.round(bad         * 10000.0 / total) / 100.0)
                .blunderRate(Math.round(blunder * 10000.0 / total) / 100.0)
                .build();
    }

    private double round3(double v) { return Math.round(v * 1000) / 1000.0; }
    private double round2(double v) { return Math.round(v * 100)  / 100.0; }

    private record WrMetric(double wrLoss, double scoreLoss) {}
    private record QualityMetric(double matchScore) {}
}