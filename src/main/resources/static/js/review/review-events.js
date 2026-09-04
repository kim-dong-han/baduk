/* ════════════════════════════════════════════════════════════
   review-events.js — DOM 이벤트 등록
   이벤트 핸들러 안에 비즈니스 로직을 넣지 않는다.
   각 기능 모듈의 함수를 호출하는 얇은 연결만 담당.
   ════════════════════════════════════════════════════════════ */

import { moves, tryState } from './review-state.js';
import { mouseToGrid } from './review-utils.js';
import {
    showMove, prevMove, nextMove, skipMove,
    jumpToInput, toggleSequence, toggleVariation, toggleOwnership,
    openCompare, closeCompare, replayFromHere,
} from './review-navigation.js';
import { toggleTryMode, exitTryMode, handleTryClick } from './review-try.js';
import { handleBoardMouseMove, handleBoardMouseLeave } from './review-candidates.js';

const canvas = document.getElementById('boardCanvas');

/* ── 바둑판 버튼 ── */
export function initNavButtons() {
    document.getElementById('btnFirst').addEventListener('click', () => showMove(0));
    document.getElementById('btnPrev5').addEventListener('click', () => skipMove(-5));
    document.getElementById('btnPrev').addEventListener('click',  () => prevMove());
    document.getElementById('btnNext').addEventListener('click',  () => nextMove());
    document.getElementById('btnNext5').addEventListener('click', () => skipMove(5));
    document.getElementById('btnLast').addEventListener('click',  () => showMove(moves.length - 1));

    // 수번 이동
    document.getElementById('btnJump')?.addEventListener('click', jumpToInput);
    // 이동 버튼 (수번 input 옆)
    document.getElementById('moveInput')?.addEventListener('keydown', e => {
        if (e.key === 'Enter') jumpToInput();
    });

    document.getElementById('btnSeq').addEventListener('click',  toggleSequence);
    document.getElementById('btnVar').addEventListener('click',  toggleVariation);
    document.getElementById('btnCmp').addEventListener('click',  openCompare);
    document.getElementById('btnTerr').addEventListener('click', toggleOwnership);
    document.getElementById('btnTry').addEventListener('click',  toggleTryMode);
    document.getElementById('btnReplay').addEventListener('click', replayFromHere);

    // 나란히 비교 닫기
    document.querySelector('.cmp-close')?.addEventListener('click', closeCompare);
    document.getElementById('compareModal')?.addEventListener('click', e => {
        if (e.target.id === 'compareModal') closeCompare();
    });
}

/* ── Canvas 이벤트 ── */
export function initCanvasEvents() {
    // 클릭: 놓아보기 착수
    canvas.addEventListener('click', e => {
        if (!tryState.active) return;
        const { gx, gy } = mouseToGrid(e, canvas);
        handleTryClick(gx, gy);
    });

    // mousemove: 후보수 hover
    canvas.addEventListener('mousemove', handleBoardMouseMove);
    canvas.addEventListener('mouseleave', handleBoardMouseLeave);

    // 마우스 휠: 수 이동 (Lizzie 식)
    let wheelLock = 0;
    canvas.addEventListener('wheel', e => {
        e.preventDefault();
        const now = performance.now();
        if (now - wheelLock < 60) return;
        wheelLock = now;
        if (e.deltaY < 0) prevMove(); else nextMove();
    }, { passive: false });
}

/* ── 키보드 ── */
export function initKeyboardEvents() {
    document.addEventListener('keydown', e => {
        if (document.activeElement === document.getElementById('moveInput')) {
            if (e.key === 'Enter') jumpToInput();
            return;
        }
        if (tryState.active) {
            if (e.key === 'Escape')     exitTryMode();
            if (e.key === 'ArrowLeft')  { e.preventDefault(); prevMove(); }
            return;   // 놓아보기 중엔 그 외 이동 단축키 비활성
        }
        if (e.key === 'ArrowLeft')  { e.preventDefault(); e.shiftKey ? skipMove(-5) : prevMove(); }
        if (e.key === 'ArrowRight') { e.preventDefault(); e.shiftKey ? skipMove(5)  : nextMove(); }
        if (e.key === 'Home')       { e.preventDefault(); showMove(0); }
        if (e.key === 'End')        { e.preventDefault(); showMove(moves.length - 1); }
        if (e.key === 'Escape')     closeCompare();
    });
}
