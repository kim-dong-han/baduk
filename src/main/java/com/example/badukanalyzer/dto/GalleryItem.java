package com.example.badukanalyzer.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 샘플 기보 갤러리 카드 한 장 — 저장된 복기 결과 요약.
 * 포트폴리오/데모용 쇼케이스 뷰(/gallery)에서 사용.
 */
@Getter
@Builder
public class GalleryItem {
    private final String id;          // /game/result/{id}
    private final String title;       // 표시용 제목
    private final boolean pro;        // 프로 기보 여부
    private final String dateText;    // YYYY-MM-DD
    private final int totalMoves;
    private final double matchRate;   // 전체 AI 유사도(%)
    private final double openingLoss; // 구간 평균 집손해
    private final double middleLoss;
    private final double endgameLoss;
    private final String bestPhase;   // 유사도 최고 구간
    private final String worstPhase;  // 유사도 최저 구간
    private final String previewMoves; // 썸네일용 착수 수순("BQ16,WD4,…", 앞 80수) — 카드에서 최종 국면을 그린다
}
