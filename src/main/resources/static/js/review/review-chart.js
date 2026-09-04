/* ════════════════════════════════════════════════════════════
   review-chart.js — 승부 흐름 Canvas 그래프
   navigation.js 에서 import 하기 때문에 navigation을 역으로
   import 하지 않는다(순환의존성 방지).
   그래프 클릭 이벤트는 main.js에서 initChart(showMove)로 등록.
   ════════════════════════════════════════════════════════════ */

import { moves, keyMistakes, losingMoveIdx, nav } from './review-state.js';

const flowCanvas = document.getElementById('flowCanvas');
const FLOW_H = 150;

let flowGeom = null;

const flowX  = i  => flowGeom.x0 + (flowGeom.n <= 1 ? 0 : (flowGeom.x1 - flowGeom.x0) * i / (flowGeom.n - 1));
const flowY  = wr => flowGeom.y1 - (flowGeom.y1 - flowGeom.y0) * Math.min(1, Math.max(0, wr));
const moveWr = m  => typeof m.winrateAfter === 'number' ? m.winrateAfter
                   : (typeof m.winrateBefore === 'number' ? m.winrateBefore : 0.5);

/** 승부 흐름 그래프 전체를 다시 그린다. showMove 내부에서 호출. */
export function drawFlowChart() {
    if (!flowCanvas || !moves.length) return;
    const cssW = flowCanvas.clientWidth || 600, dpr = window.devicePixelRatio || 1;
    flowCanvas.width  = cssW * dpr;
    flowCanvas.height = FLOW_H * dpr;
    flowCanvas.style.height = FLOW_H + 'px';
    const ctx = flowCanvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, cssW, FLOW_H);

    const padL = 40, padR = 12, padT = 10, padB = 18;
    flowGeom = { x0: padL, x1: cssW - padR, y0: padT, y1: FLOW_H - padB, n: moves.length };
    const midY = flowY(0.5);

    // 배경: 위=흑 우세, 아래=백 우세
    ctx.fillStyle = '#eef1f4';
    ctx.fillRect(padL, padT, flowGeom.x1 - padL, midY - padT);
    ctx.fillStyle = '#faf7f2';
    ctx.fillRect(padL, midY, flowGeom.x1 - padL, flowGeom.y1 - midY);

    // 50% 점선
    ctx.strokeStyle = '#c8ccd2'; ctx.lineWidth = 1; ctx.setLineDash([4, 4]);
    ctx.beginPath(); ctx.moveTo(padL, midY); ctx.lineTo(flowGeom.x1, midY); ctx.stroke();
    ctx.setLineDash([]);

    // y축 라벨
    ctx.fillStyle = '#999'; ctx.font = '10px sans-serif';
    ctx.textAlign = 'right'; ctx.textBaseline = 'middle';
    ctx.fillText('흑 100%', padL - 5, flowY(1));
    ctx.fillText('50%',     padL - 5, midY);
    ctx.fillText('백 100%', padL - 5, flowY(0));

    // 면적 (라인~50%선)
    ctx.beginPath();
    moves.forEach((m, i) => {
        const x = flowX(i), y = flowY(moveWr(m));
        i ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
    });
    ctx.lineTo(flowX(moves.length - 1), midY);
    ctx.lineTo(flowX(0), midY);
    ctx.closePath();
    ctx.fillStyle = 'rgba(44,62,80,0.12)'; ctx.fill();

    // 승률 라인
    ctx.beginPath();
    moves.forEach((m, i) => {
        const x = flowX(i), y = flowY(moveWr(m));
        i ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
    });
    ctx.strokeStyle = '#2c3e50'; ctx.lineWidth = 1.8; ctx.stroke();

    // 실수·악수 마커
    for (const m of keyMistakes) {
        const i = m.turnNumber - 1;
        if (i < 0 || i >= moves.length) continue;
        const x = flowX(i), y = flowY(moveWr(moves[i])), big = m.scoreLoss >= 5;
        ctx.beginPath(); ctx.arc(x, y, big ? 4 : 3, 0, Math.PI * 2);
        ctx.fillStyle = big ? '#c0392b' : '#e67e22'; ctx.fill();
    }

    // 패착 마커
    if (losingMoveIdx >= 0 && losingMoveIdx < moves.length) {
        const x = flowX(losingMoveIdx), y = flowY(moveWr(moves[losingMoveIdx]));
        ctx.beginPath(); ctx.arc(x, y, 5.5, 0, Math.PI * 2);
        ctx.fillStyle = '#f1c40f'; ctx.strokeStyle = '#b7950b'; ctx.lineWidth = 1.5;
        ctx.fill(); ctx.stroke();
    }

    // 현재 수 커서
    if (nav.currentIdx >= 0 && nav.currentIdx < moves.length) {
        const x = flowX(nav.currentIdx);
        ctx.strokeStyle = '#3498db'; ctx.lineWidth = 1.5;
        ctx.beginPath(); ctx.moveTo(x, padT); ctx.lineTo(x, flowGeom.y1); ctx.stroke();
        ctx.beginPath(); ctx.arc(x, flowY(moveWr(moves[nav.currentIdx])), 3.5, 0, Math.PI * 2);
        ctx.fillStyle = '#3498db'; ctx.fill();
    }

    // x축 라벨
    ctx.fillStyle = '#aaa'; ctx.font = '10px sans-serif';
    ctx.textAlign = 'center'; ctx.textBaseline = 'top';
    ctx.fillText('1수',             flowX(0),              flowGeom.y1 + 3);
    ctx.fillText(moves.length + '수', flowX(moves.length - 1), flowGeom.y1 + 3);
}

/**
 * 그래프 클릭(수 이동)·resize 이벤트를 등록.
 * @param {function(number): void} onMoveTo - showMove 콜백
 */
export function initChart(onMoveTo) {
    if (!flowCanvas) return;
    flowCanvas.addEventListener('click', e => {
        if (!flowGeom) return;
        const rect = flowCanvas.getBoundingClientRect();
        const frac = (e.clientX - rect.left - flowGeom.x0) / (flowGeom.x1 - flowGeom.x0);
        onMoveTo(Math.min(moves.length - 1, Math.max(0, Math.round(frac * (moves.length - 1)))));
    });
    let resizeTimer;
    window.addEventListener('resize', () => {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(drawFlowChart, 150);
    });
}
