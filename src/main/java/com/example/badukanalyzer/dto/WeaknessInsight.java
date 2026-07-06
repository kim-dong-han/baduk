package com.example.badukanalyzer.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 실력 리포트 "반복 약점" 자동 코멘트 한 줄.
 * 저장된 복기 결과를 여러 판에 걸쳐 집계해 만든, 일반인 친화 코칭 문구.
 */
@Getter
@Builder
public class WeaknessInsight {
    private final String icon;      // 이모지
    private final String title;     // 짧은 헤드라인
    private final String detail;    // 수치 포함 설명
    private final String severity;  // high / mid / good (색상 구분)
}
