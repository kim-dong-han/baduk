package com.example.badukanalyzer.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisResponse {
    private final String phase;
    private final double matchRate;
    private final double winRateLoss;
    private final double scoreLoss;

    // 수질 등급 분포 (각 비율 0~100)
    private final double excellentRate; // 최선  (≥95%)
    private final double goodRate;      // 준최선 (≥80%)
    private final double normalRate;    // 보통   (≥60%)
    private final double badRate;       // 악수   (≥35%)
    private final double blunderRate;   // 실수   (<35%)
}
