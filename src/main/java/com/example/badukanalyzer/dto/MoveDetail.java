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
public class MoveDetail {
    private int turnNumber;        // 1-indexed (표시용)
    private String color;          // "B" or "W"
    private String move;           // 실제 착점 (GTP: "D4")
    private String bestMove;        // KataGo 최선수 (1위)
    private List<String> topMoves; // KataGo 추천수 상위 3개 GTP 좌표 (1·2·3위)
    private List<String> bestPv;   // 최선수 이후 예상 진행 (변화도, GTP 좌표 순서)

    private boolean matchesBest;

    private double winrateBefore;  // 착점 전 흑 승률
    private double winrateAfter;   // 착점 후 흑 승률
    private double winrateLoss;    // > 0 이면 현재 플레이어에게 손해

    private double scoreLeadBefore;
    private double scoreLeadAfter;
    private double scoreLoss;      // > 0 이면 현재 플레이어에게 손해 (집 기준)

    private String grade;          // S / A / B / C / D
    private String phase;          // 초반 / 중반 / 종반

    private List<Double> ownership; // AI 집(영역) 예측 361칸(19x19), +값=흑·−값=백. 착점 후 국면 기준. 구 JSON은 null

    private List<Candidate> candidates; // AI 후보수 상위 N개 상세(hover용). 구 JSON은 null

    /** 후보수 1개 상세 — hover 시 승률·집차·예상 진행 표시 (승률·집차는 흑 기준) */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        private String move;         // GTP 좌표
        private double winrate;      // 흑 승률 0~1
        private double scoreLead;    // 흑 기준 집 차
        private List<String> pv;     // 이 수 이후 예상 진행 (GTP, 최대 6수)
    }
}
