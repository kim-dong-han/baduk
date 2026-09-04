/* ════════════════════════════════════════════════════════════
   review-main.js — 복기 화면 진입점 + 초기화
   ES Module 로 로드: <script type="module" src="/js/review/review-main.js">
   ════════════════════════════════════════════════════════════ */

import { moves, keyMistakes }       from './review-state.js';
import { BOARD_PX }                  from './review-utils.js';
import { showMove }                   from './review-navigation.js';
import { initChart, drawFlowChart }  from './review-chart.js';
import { registerTryCallbacks }      from './review-try.js';
import { initNavButtons, initCanvasEvents, initKeyboardEvents } from './review-events.js';
import {
    renderKeyMistakes, renderLosingCard, renderAiStats,
    renderRankEstimate, buildGameSummary, buildGoodMoves,
    buildLessonCards, fillSummarySlots, initReportEvents,
} from './review-report.js';

/* ── 바둑판 캔버스 초기 설정 ── */
const canvas = document.getElementById('boardCanvas');
canvas.width  = BOARD_PX;
canvas.height = BOARD_PX;
canvas.style.maxWidth = BOARD_PX + 'px';
canvas.style.height   = 'auto';

/* ── 이벤트 등록 ── */
initNavButtons();
initCanvasEvents();
initKeyboardEvents();
registerTryCallbacks();   // prevMove 내부에서 쓸 try 콜백 주입
initChart(showMove);      // 승부 흐름 그래프 클릭 → showMove
initReportEvents();       // 리포트 카드 클릭 위임 등록

/* ── 정적 리포트 렌더 (이동 없이 바로 표시할 것들) ── */
renderKeyMistakes();
renderLosingCard();
renderAiStats();

/* ── DOMContentLoaded 이후 초기 수 표시 + 동적 리포트 ── */
document.addEventListener('DOMContentLoaded', () => {
    if (!moves.length) return;

    // ?move=N 딥링크 처리 (오답노트 등에서 특정 수로 바로 이동)
    const qMove   = parseInt(new URLSearchParams(location.search).get('move'));
    const initIdx = (!isNaN(qMove) && qMove >= 1 && qMove <= moves.length)
        ? qMove - 1
        : (keyMistakes.length > 0 ? keyMistakes[0].turnNumber - 1 : 0);

    showMove(initIdx >= 0 ? initIdx : 0);

    renderRankEstimate();
    buildGameSummary();
    buildGoodMoves();
    buildLessonCards();
    fillSummarySlots();
    drawFlowChart();
});
