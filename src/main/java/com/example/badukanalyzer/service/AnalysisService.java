package com.example.badukanalyzer.service;

import com.example.badukanalyzer.converter.SgfConverter;
import com.example.badukanalyzer.domain.Move;
import com.example.badukanalyzer.dto.AnalysisResponse;
import com.example.badukanalyzer.parser.GibParser;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    private final KataGoService kataGoService;

    @Value("${katago.record-dir}")
    private String recordDir;

    public AnalysisService(KataGoService kataGoService) {
        this.kataGoService = kataGoService;
    }

    public List<AnalysisResponse> analyzeBatch() throws Exception {
        File dir = new File(recordDir);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".gib"));
        
        if (files == null || files.length == 0) {
            throw new IOException("분석할 GIB 파일이 없습니다: " + recordDir);
        }

        List<AnalysisMetrics> allOpening = new ArrayList<>();
        List<AnalysisMetrics> allMiddle = new ArrayList<>();
        List<AnalysisMetrics> allEndgame = new ArrayList<>();

        // 최대 3판 또는 존재하는 파일 수만큼 분석 (테스트를 위해 축소)
        int count = 0;
        GibParser parser = new GibParser();
        SgfConverter converter = new SgfConverter();

        for (File file : files) {
            if (count++ >= 3) break;
            System.out.println("분석 시작: " + file.getName());
            
            // 1. GIB 파싱 및 SGF 변환
            List<Move> moves = parser.parse(file.getAbsolutePath());
            String sgfContent = converter.convert(moves);
            
            // 2. 카타고 분석 (모든 수 분석 요청)
            List<JsonNode> analysisData = kataGoService.analyzeSgf(sgfContent, moves.size());
            
            // 3. 구간별 데이터 분류
            processGameData(analysisData, moves, allOpening, allMiddle, allEndgame);
            System.out.println("분석 완료: " + file.getName() + " (데이터 수: " + analysisData.size() + ")");
        }

        List<AnalysisResponse> result = new ArrayList<>();
        result.add(calculateSummary("초반", allOpening));
        result.add(calculateSummary("중반", allMiddle));
        result.add(calculateSummary("종반", allEndgame));

        return result;
    }

    private void processGameData(List<JsonNode> analysisData, List<Move> actualMoves, List<AnalysisMetrics> opening, List<AnalysisMetrics> middle, List<AnalysisMetrics> endgame) {
        int openingEnd = 50;
        int middleEnd = 150;

        double prevWinrate = 0.5;
        double prevScoreLead = 0.0;

        // turnNumber별로 정렬되어 있다고 가정 (카타고 출력 순서)
        for (JsonNode node : analysisData) {
            if (!node.has("rootInfo") || !node.has("turnNumber")) continue;
            
            int turnNumber = node.get("turnNumber").asInt();
            double currentWinrate = node.get("rootInfo").get("winrate").asDouble();
            double currentScoreLead = node.get("rootInfo").get("scoreLead").asDouble();

            double wrLoss = Math.abs(currentWinrate - prevWinrate);
            double scoreLoss = Math.abs(currentScoreLead - prevScoreLead);
            
            boolean isMatch = false;
            // 해당 turnNumber에서 두어진 실제 수 찾기
            // turnNumber 0은 착수 전 상태, turnNumber 1은 1번째 수(index 0)가 두어진 후 상태
            // 카타고 일치율은 해당 '상태'에서 추천된 수와 '실제 두어진 수'를 비교해야 함
            // 즉, turnNumber N에서의 추천 수와 actualMoves[N]을 비교
            if (turnNumber < actualMoves.size() && node.has("moveInfos") && node.get("moveInfos").size() > 0) {
                Move actualMove = actualMoves.get(turnNumber);
                String actualMoveSgf = convertToSgfCoord(actualMove);
                String recommendedMoveSgf = node.get("moveInfos").get(0).get("move").asText();
                
                isMatch = actualMoveSgf.equalsIgnoreCase(recommendedMoveSgf);
            }

            AnalysisMetrics metric = new AnalysisMetrics(wrLoss, scoreLoss, isMatch);
            
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

    private String convertToSgfCoord(Move move) {
        char[] SGF_CHARS = "abcdefghijklmnopqrs".toCharArray();
        return "" + SGF_CHARS[move.getX()] + SGF_CHARS[18 - move.getY()];
    }

    private AnalysisResponse calculateSummary(String phase, List<AnalysisMetrics> metrics) {
        if (metrics.isEmpty()) {
            return AnalysisResponse.builder().phase(phase).matchRate(0).winRateLoss(0).scoreLoss(0).build();
        }

        double avgWrLoss = metrics.stream().mapToDouble(m -> m.wrLoss).average().orElse(0);
        double avgScoreLoss = metrics.stream().mapToDouble(m -> m.scoreLoss).average().orElse(0);
        long matches = metrics.stream().filter(m -> m.isMatch).count();
        double matchRate = (double) matches / metrics.size() * 100;

        return AnalysisResponse.builder()
                .phase(phase)
                .matchRate(Math.round(matchRate * 100) / 100.0)
                .winRateLoss(Math.round(avgWrLoss * 1000) / 1000.0)
                .scoreLoss(Math.round(avgScoreLoss * 100) / 100.0)
                .build();
    }

    private static class AnalysisMetrics {
        double wrLoss;
        double scoreLoss;
        boolean isMatch;

        AnalysisMetrics(double wrLoss, double scoreLoss, boolean isMatch) {
            this.wrLoss = wrLoss;
            this.scoreLoss = scoreLoss;
            this.isMatch = isMatch;
        }
    }
}
