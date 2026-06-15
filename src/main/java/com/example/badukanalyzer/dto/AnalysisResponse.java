package com.example.badukanalyzer.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisResponse {
    private final String phase; // 초반, 중반, 종반
    private final double matchRate; // 카타고 일치율
    private final double winRateLoss; // 평균 승률 손실
    private final double scoreLoss; // 평균 집수 손실
}
