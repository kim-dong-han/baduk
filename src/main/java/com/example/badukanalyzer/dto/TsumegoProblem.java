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
public class TsumegoProblem {
    private String id;
    private String difficulty;        // 쉬움 / 보통 / 어려움
    private String toPlay;            // "B" or "W"
    private String prompt;            // 문제 지시문
    private List<Stone> stones;       // 초기 배치
    private List<String> answers;     // 정답 첫 수 (GTP, 복수 허용)
    private List<String> solution;    // 정해 진행 (GTP)
    private int[] region;             // [xMin, yMin, xMax, yMax] 격자(0..18, y=하단기준) - 코너 확대용

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stone {
        private String color;         // "B" / "W"
        private String at;            // GTP 좌표 (예: "Q16")
    }
}
