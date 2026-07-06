package com.example.badukanalyzer.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 오답노트 한 항목 — 내 기보에서 실수·악수로 분류된 한 수.
 * 저장된 복기 결과에서 추려 만들며, key(gameId:turn)로 클라이언트 북마크를 매긴다.
 */
@Getter
@Builder
public class MistakeNote {
    private final String gameId;      // 복기 결과 UUID → /game/result/{id}?move={turn}
    private final String fileName;
    private final String blackPlayer;
    private final String whitePlayer;
    private final String analyzedAt;
    private final int turnNumber;
    private final String color;       // B / W
    private final String move;        // 실제 착점 (GTP)
    private final String bestMove;    // AI 최선수
    private final String grade;       // 실수 / 악수
    private final double scoreLoss;   // 집 손해
    private final String phase;       // 초반 / 중반 / 종반
}
