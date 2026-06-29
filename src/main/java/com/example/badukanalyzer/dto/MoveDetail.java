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
}
