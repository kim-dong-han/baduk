package com.example.badukanalyzer.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 한 대국자의 누적 기력(기획 A) + 성장 추이(기획 B).
 *
 * 기존 화면의 기력 표시는 한 판만 보고 계산했다. 한 판은 운·상대·판의 성격에 크게 흔들려
 * 기력이라 부르기 어렵다. 여기서는 저장된 복기 결과 전체를 대국자 이름으로 묶어 여러 판을
 * 누적하고, 표본이 얼마나 되는지(confidence)를 값과 함께 들고 다닌다.
 * KataGo 를 다시 돌리지 않고 저장된 MoveDetail·PhaseStats 만 읽는다.
 */
@Getter
@Builder
public class PlayerRating {

    private final String name;
    private final boolean pro;          // 파일명이 프로 기보로 표시된 판에서만 나온 대국자

    private final int games;            // 집계에 들어간 판 수
    private final int moves;            // 집계에 들어간 착수 수(그 대국자의 수만)

    private final double avgScoreLoss;  // 한 수당 평균 집손해 — 기력 산출의 기준값
    private final double matchRate;     // AI 최선수 일치율(%)

    private final String band;          // "아마 상급" 등 6구간
    private final String bandSub;       // "약 1~5급"
    private final String bandColor;

    /** 표본 신뢰도: 확정 / 잠정 / 표본 부족 */
    private final String confidence;
    private final String confidenceNote;

    /** 구간별 기력 — 한 줄 등급이 못 담는 약점을 드러낸다. 표본 없으면 null */
    private final PhaseRating opening;
    private final PhaseRating middle;
    private final PhaseRating endgame;

    /** 가장 강한/약한 구간 이름(구간 3개가 다 있을 때만) */
    private final String strongPhase;
    private final String weakPhase;

    /** 기복 — 판별 평균 집손해의 표준편차. 작을수록 일정한 기력을 낸다 */
    private final double volatility;
    private final String steadiness;    // 안정형 / 보통 / 기복형

    /* ── 기획 B: 성장 ── */
    private final boolean growthReady;  // 앞·뒤 구간에 각각 최소 판 수가 찼는지
    private final double earlyLoss;     // 앞쪽 절반 평균 집손해
    private final double recentLoss;    // 뒤쪽 절반 평균 집손해
    private final double growth;        // earlyLoss - recentLoss (양수 = 좋아짐, 집/수)
    private final double growthPct;     // 개선율(%)
    private final int earlyGames;
    private final int recentGames;

    /* 화면 표시용 파생값 — 템플릿에서 계산하지 않는다.
       Thymeleaf/SpEL 에서 T(java.lang.Math).min/abs 를 부르면 오버로드가 모호해 터진다
       (EL1033E). 부호와 절대값 분리, 막대 길이 같은 건 전부 여기서 끝내 둔다. */
    private final boolean improved;     // growth > 0
    private final double growthAbs;     // |growth|
    private final double growthPctAbs;  // |growthPct|

    /** 판별 평균 집손해(과거 → 최근 순) — 추이 스파크라인용 */
    private final List<Double> lossTrend;
    private final List<String> gameLabels;

    @Getter
    @Builder
    public static class PhaseRating {
        private final String phase;
        private final int moves;
        private final double avgScoreLoss;
        private final double matchRate;
        private final String band;
        private final String bandSub;
        private final String bandColor;
        /** 막대 길이(%). 5집을 상한으로 본 손해 비율 — 템플릿에서 min 을 부르지 않으려고 여기서 자른다. */
        private final double barPct;
    }
}
