package com.example.badukanalyzer.service;

import com.example.badukanalyzer.dto.AnalysisResponse;
import com.example.badukanalyzer.dto.MoveDetail;
import com.example.badukanalyzer.dto.SingleGameResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 실력 리포트(/analysis/batch) 데이터 공급.
 * KataGo를 다시 돌리지 않고, /game 복기에서 이미 저장된 결과(GameResults/*.json)를
 * 내 기보 vs 프로(파일명 "신진서 vs") 그룹으로 나눠 구간별로 집계한다.
 */
@Service
public class AnalysisService {

    private static final String[] PHASES = {"초반", "중반", "종반"};
    private static final String PRO_MARKER = "신진서 vs";

    private final SingleGameService singleGameService;
    private volatile String errorMessage;

    public AnalysisService(SingleGameService singleGameService) {
        this.singleGameService = singleGameService;
    }

    public List<AnalysisResponse> getUserResults() { return aggregate(false); }
    public List<AnalysisResponse> getProResults()  { return aggregate(true); }
    public Object getProWinrateTrend()  { return null; }   // 현재 템플릿 미사용
    public Object getUserWinrateTrend() { return null; }
    public String getErrorMessage()     { return errorMessage; }
    public boolean isRunning()          { return false; }  // 즉시 집계라 백그라운드 작업 없음

    private List<AnalysisResponse> aggregate(boolean pro) {
        List<SingleGameResult> games;
        try {
            games = singleGameService.listResults();   // 파일명 기준 최신 분석만(중복 제거)
            errorMessage = null;
        } catch (Exception e) {
            errorMessage = "분석 결과를 읽지 못했습니다: " + e.getMessage();
            return List.of();
        }

        List<AnalysisResponse> out = new ArrayList<>();
        for (String phase : PHASES) {
            int total = 0, match = 0, ex = 0, gd = 0, nm = 0, bd = 0, bl = 0;
            double scoreLossSum = 0, wrLossSum = 0;

            for (SingleGameResult g : games) {
                boolean isPro = g.getFileName() != null && g.getFileName().contains(PRO_MARKER);
                if (isPro != pro || g.getMoves() == null) continue;

                for (MoveDetail m : g.getMoves()) {
                    if (!phase.equals(m.getPhase())) continue;
                    total++;
                    if (m.isMatchesBest()) match++;
                    scoreLossSum += m.getScoreLoss();
                    wrLossSum    += m.getWinrateLoss();
                    switch (m.getGrade() == null ? "" : m.getGrade()) {
                        case "최선" -> ex++;
                        case "좋음" -> gd++;
                        case "보통" -> nm++;
                        case "실수" -> bd++;
                        case "악수" -> bl++;
                    }
                }
            }
            if (total == 0) continue;

            out.add(AnalysisResponse.builder()
                    .phase(phase)
                    .matchRate(round2(match * 100.0 / total))
                    .winRateLoss(round3(wrLossSum / total))
                    .scoreLoss(round2(scoreLossSum / total))
                    .excellentRate(round2(ex * 100.0 / total))
                    .goodRate(round2(gd * 100.0 / total))
                    .normalRate(round2(nm * 100.0 / total))
                    .badRate(round2(bd * 100.0 / total))
                    .blunderRate(round2(bl * 100.0 / total))
                    .build());
        }
        return out;
    }

    private double round2(double v) { return Math.round(v * 100)  / 100.0; }
    private double round3(double v) { return Math.round(v * 1000) / 1000.0; }
}
