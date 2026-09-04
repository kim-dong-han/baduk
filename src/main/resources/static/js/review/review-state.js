/* ════════════════════════════════════════════════════════════
   review-state.js — 복기 화면 공유 상태 (단일 소스)
   Thymeleaf 주입 데이터(window.REVIEW_DATA)에서 시작.
   모든 모듈이 이 파일에서 상태를 읽는다.
   ════════════════════════════════════════════════════════════ */

export const moves = window.REVIEW_DATA?.moves ?? [];

/* ── 분석 파생 데이터 (불변, 앱 시작 시 1회 계산) ── */

export const keyMistakes = moves
    .filter(m => m.grade === '실수' || m.grade === '악수')
    .sort((a, b) => b.scoreLoss - a.scoreLoss);

export const mistakeTurns = new Set(keyMistakes.map(m => m.turnNumber));

/** 집 이득을 만든 호수(scoreLoss ≤ -2), 이득 큰 순 3개 */
export const goodMoves = moves
    .filter(m => typeof m.scoreLoss === 'number' && m.scoreLoss <= -2.0)
    .sort((a, b) => a.scoreLoss - b.scoreLoss)
    .slice(0, 3);

/* ── 패착 판정 ── */

/**
 * 흑 기준 winrate → '이 수를 둔 쪽' 기준 winrate 변환.
 * @param {object} m - move 객체
 * @param {boolean} before - true=두기 전, false=두고 난 후
 */
export function moverWr(m, before) {
    const w = before ? m.winrateBefore : m.winrateAfter;
    return m.color === 'B' ? w : 1 - w;
}

function findLosingMove() {
    /* 후보: 팽팽했던(≥40%) 국면에서 승률을 크게 무너뜨려 열세로 넘긴 뒤 회복 못한 수 */
    const cand = [];
    for (let i = 0; i < moves.length; i++) {
        const m = moves[i];
        if (!(m.scoreLoss >= 1.5)) continue;
        if (m.winrateBefore == null || m.winrateAfter == null) continue;
        const pre = moverWr(m, true), post = moverWr(m, false);
        if (pre < 0.40 || post >= 0.50 || pre - post <= 0) continue;
        let recovered = false;
        for (let j = i + 1; j < moves.length; j++) {
            const meWr = m.color === 'B' ? moves[j].winrateAfter : 1 - moves[j].winrateAfter;
            if (meWr >= 0.50) { recovered = true; break; }
        }
        cand.push({ i, swing: pre - post, recovered });
    }
    if (cand.length === 0) return -1;
    const stuck = cand.filter(c => !c.recovered);
    const pool  = stuck.length ? stuck : cand;
    pool.sort((a, b) => b.swing - a.swing);
    return pool[0].i;
}

export const losingMoveIdx = findLosingMove();
export const losingMove    = losingMoveIdx >= 0 ? moves[losingMoveIdx] : null;

/* ── 내비게이션 상태 (가변) ── */
export const nav = {
    currentIdx:    -1,
    showSequence:  false,
    showVariation: false,
    showOwnership: false,
};

/* ── 후보수 hover 상태 (가변) ── */
export const candState = {
    markers:    [],   // {gx, gy, rank, cand}[]  현재 화면 후보 마커
    hoveredIdx: -1,   // 현재 hover 중인 마커 인덱스
};

/* ── 놓아보기 상태 (가변) ── */
export const tryState = {
    active:       false,
    baseIdx:      -1,
    stones:       [],      // {x, y, color}[]  놓은 돌
    recos:        [],      // {gx, gy, rank, cand}[]  AI 추천수 마커
    recoSide:     'B',     // 추천수를 계산한 '둘 차례' 색
    analyzeToken: 0,       // race condition 방지용 토큰
    prevBest:     null,    // 직전 국면 최선 승률% (방금 둔 수 평가 기준)
};
