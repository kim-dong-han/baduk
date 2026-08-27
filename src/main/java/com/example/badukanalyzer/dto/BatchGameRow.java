package com.example.badukanalyzer.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 실력 리포트(/analysis/batch)의 판 단위 요약 한 줄.
 * 저장된 복기 결과(GameResults/*.json)에서만 뽑는다 — 재분석 없음.
 * 실력 변화 추이 그래프와 "최근 분석 기보" 표가 같은 데이터를 쓴다.
 */
@Getter
@Builder
public class BatchGameRow {
    private final String id;          // /game/result/{id}
    private final String title;       // 대국자 또는 파일명
    private final String dateText;    // YYYY-MM-DD
    private final String shortDate;   // MM.DD
    private final int totalMoves;
    private final double matchRate;   // 판 전체 AI 최선수 일치율(%) — 구간 수 가중평균
    private final double avgScoreLoss;// 한 수당 평균 집손해
    private final int mistakeCount;   // 3집 이상 5집 미만
    private final int blunderCount;   // 5집 이상
}
