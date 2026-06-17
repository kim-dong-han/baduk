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
import java.io.IOException;
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
                userResults = analyzeByPrefix("", 5);
                proResults = List.of(); // 프로 기보 분석 임시 비활성화
            } catch (Exception e) {
                errorMessage = e.getMessage();
            } finally {
                running = false;
            }
        });
    }

    public List<AnalysisResponse> getUserResults() {
        return userResults;
    }

    public List<AnalysisResponse> getProResults() {
        return proResults;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isRunning() {
        return running;
    }

    private List<AnalysisResponse> analyzeByPrefix(String prefix, int maxFiles) throws Exception {
        File dir = new File(recordDir);
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            boolean matchesPrefix = prefix.isEmpty() ? !lower.startsWith("pro_") : lower.startsWith(prefix);
            return matchesPrefix && (lower.endsWith(".gib") || lower.endsWith(".sgf"));
        });

        if (files == null || files.length == 0) {
            String label = prefix.isEmpty() ? "내 기보" : "프로 기보";
            System.out.println(label + " 파일 없음");
            return List.of();
        }

        GibParser gibParser = new GibParser();
        SgfParser sgfParser = new SgfParser();
        List<List<Move>> allMoves = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();

        int count = 0;
        for (File file : files) {
            if (count++ >= maxFiles) break;
            System.out.println("파싱 (" + prefix + "): " + file.getName());
            String path = file.getAbsolutePath();
            if (path.toLowerCase().endsWith(".gib")) {
                allMoves.add(gibParser.parse(path));
            } else {
                allMoves.add(sgfParser.parse(path));
            }
            fileNames.add(file.getName());
        }

        if (allMoves.isEmpty()) return List.of();

        String label = prefix.isEmpty() ? "내 기보" : "프로 기보";
        System.out.println(label + " KataGo 분석 시작 (" + allMoves.size() + "개)");
        long t0 = System.currentTimeMillis();
        List<List<JsonNode>> allResults = kataGoService.analyzeMultipleGames(allMoves);
        long t1 = System.currentTimeMillis();
        System.out.println(label + " 분석 완료 (" + (t1-t0)/1000.0 + "초)");

        List<AnalysisMetrics> allOpening = new ArrayList<>();
        List<AnalysisMetrics> allMiddle = new ArrayList<>();
        List<AnalysisMetrics> allEndgame = new ArrayList<>();

        for (int i = 0; i < allMoves.size(); i++) {
            System.out.println("결과 처리: " + fileNames.get(i) + " (데이터 수: " + allResults.get(i).size() + ")");
            processGameData(allResults.get(i), allMoves.get(i), allOpening, allMiddle, allEndgame);
        }

        List<AnalysisResponse> result = new ArrayList<>();
        if (!allOpening.isEmpty()) result.add(calculateSummary("초반", allOpening));
        if (!allMiddle.isEmpty()) result.add(calculateSummary("중반", allMiddle));
        if (!allEndgame.isEmpty()) result.add(calculateSummary("종반", allEndgame));

        return result;
    }

    private void processGameData(List<JsonNode> analysisData, List<Move> actualMoves, List<AnalysisMetrics> opening, List<AnalysisMetrics> middle, List<AnalysisMetrics> endgame) {
        int openingEnd = 50;
        int middleEnd = 150;

        double prevWinrate = 0.5;
        double prevScoreLead = 0.0;

        for (JsonNode node : analysisData) {
            if (!node.has("rootInfo") || !node.has("turnNumber")) continue;

            int turnNumber = node.get("turnNumber").asInt();
            double currentWinrate = node.get("rootInfo").get("winrate").asDouble();
            double currentScoreLead = node.get("rootInfo").get("scoreLead").asDouble();

            double wrLoss = Math.abs(currentWinrate - prevWinrate);
            double scoreLoss = Math.abs(currentScoreLead - prevScoreLead);

            // 흑 턴이면 높은 winrate가 좋고, 백 턴이면 낮은 winrate(흑 기준)가 좋음
            boolean isBlackTurn = (turnNumber % 2 == 0);

            double matchScore = 0.6; // 기본값: 후보수 목록에 없는 경우 중간값
            if (turnNumber < actualMoves.size() && node.has("moveInfos") && node.get("moveInfos").size() > 0) {
                Move actualMove = actualMoves.get(turnNumber);
                String actualMoveGtp = CoordinateConverter.toGtpCoord(actualMove);

                double topWinrate = node.get("moveInfos").get(0).get("winrate").asDouble();
                double actualWinrate = -1;
                for (JsonNode moveInfo : node.get("moveInfos")) {
                    if (actualMoveGtp.equalsIgnoreCase(moveInfo.get("move").asText())) {
                        actualWinrate = moveInfo.get("winrate").asDouble();
                        break;
                    }
                }

                if (actualWinrate >= 0) {
                    if (isBlackTurn) {
                        // 흑: 높은 winrate가 좋음
                        matchScore = topWinrate > 0 ? Math.min(1.0, actualWinrate / topWinrate) : 0.6;
                    } else {
                        // 백: 낮은 winrate(흑 기준)가 좋음
                        double denom = 1.0 - topWinrate;
                        matchScore = denom > 0.01 ? Math.min(1.0, (1.0 - actualWinrate) / denom) : 0.6;
                    }
                }
            }

            AnalysisMetrics metric = new AnalysisMetrics(wrLoss, scoreLoss, matchScore);

            if (turnNumber <= openingEnd) {
                opening.add(metric);
            } else if (turnNumber <= middleEnd) {
                middle.add(metric);
            } else {
                endgame.add(metric);
            }

            prevWinrate = currentWinrate;
            prevScoreLead = currentScoreLead;
        }
    }

    private AnalysisResponse calculateSummary(String phase, List<AnalysisMetrics> metrics) {
        if (metrics.isEmpty()) {
            return AnalysisResponse.builder().phase(phase).matchRate(0).winRateLoss(0).scoreLoss(0)
                    .excellentRate(0).goodRate(0).normalRate(0).badRate(0).blunderRate(0).build();
        }

        int total = metrics.size();
        double avgWrLoss = metrics.stream().mapToDouble(m -> m.wrLoss).average().orElse(0);
        double avgScoreLoss = metrics.stream().mapToDouble(m -> m.scoreLoss).average().orElse(0);
        double avgMatchScore = metrics.stream().mapToDouble(m -> m.matchScore).average().orElse(0);

        long excellent = metrics.stream().filter(m -> m.matchScore >= 0.95).count();
        long good      = metrics.stream().filter(m -> m.matchScore >= 0.80 && m.matchScore < 0.95).count();
        long normal    = metrics.stream().filter(m -> m.matchScore >= 0.60 && m.matchScore < 0.80).count();
        long bad       = metrics.stream().filter(m -> m.matchScore >= 0.35 && m.matchScore < 0.60).count();
        long blunder   = metrics.stream().filter(m -> m.matchScore < 0.35).count();

        return AnalysisResponse.builder()
                .phase(phase)
                .matchRate(Math.round(avgMatchScore * 10000) / 100.0)
                .winRateLoss(Math.round(avgWrLoss * 1000) / 1000.0)
                .scoreLoss(Math.round(avgScoreLoss * 100) / 100.0)
                .excellentRate(Math.round(excellent * 10000.0 / total) / 100.0)
                .goodRate(Math.round(good * 10000.0 / total) / 100.0)
                .normalRate(Math.round(normal * 10000.0 / total) / 100.0)
                .badRate(Math.round(bad * 10000.0 / total) / 100.0)
                .blunderRate(Math.round(blunder * 10000.0 / total) / 100.0)
                .build();
    }

    private static class AnalysisMetrics {
        double wrLoss;
        double scoreLoss;
        double matchScore;

        AnalysisMetrics(double wrLoss, double scoreLoss, double matchScore) {
            this.wrLoss = wrLoss;
            this.scoreLoss = scoreLoss;
            this.matchScore = matchScore;
        }
    }
}
