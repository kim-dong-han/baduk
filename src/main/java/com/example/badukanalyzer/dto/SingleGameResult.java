package com.example.badukanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SingleGameResult {
    private String id;           // UUID (저장 파일명 기준)
    private String fileName;
    private String analyzedAt;   // ISO-8601 문자열
    private int totalMoves;

    private List<MoveDetail> moves;       // 전체 수 상세
    private List<MoveDetail> top3Mistakes;  // scoreLoss 낙폭 Top3
    private List<MoveDetail> top3GoodMoves; // scoreLoss 이득 Top3

    private PhaseStats opening;   // 1~50수
    private PhaseStats middle;    // 51~150수
    private PhaseStats endgame;   // 151수~

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhaseStats {
        private String phase;
        private int moveCount;
        private double avgScoreLoss;
        private double avgWinrateLoss;
        private double matchRate;      // 최선수 일치율 (%)
        private int blunderCount;      // scoreLoss >= 5
        private int mistakeCount;      // 3 <= scoreLoss < 5
    }
}
