/* ════════════════════════════════════════════════════════════
   review-candidates.js — AI 후보수 hover · 툴팁
   ════════════════════════════════════════════════════════════ */

import { moves, nav, candState, tryState } from './review-state.js';
import { BOARD_PX, mouseToGrid, wrPct1 } from './review-utils.js';
import { buildBoardState, drawBoard, drawStone, drawPvSequence } from './review-board.js';
import { showMove } from './review-navigation.js';
import { gtpToGrid } from './review-utils.js';

const canvas  = document.getElementById('boardCanvas');
const tooltip = document.getElementById('candTooltip');

/* ── hover 진입: PV 표시 + 툴팁 ── */

export function renderCandidateHover(marker) {
    const ctx = canvas.getContext('2d');
    const cur = moves[nav.currentIdx];
    drawBoard(ctx);
    const base  = buildBoardState(nav.currentIdx - 1);
    const prevG = nav.currentIdx > 0 ? gtpToGrid(moves[nav.currentIdx - 1].move) : null;
    for (let x = 0; x < 19; x++)
        for (let y = 0; y < 19; y++)
            if (base[x][y]) drawStone(ctx, x, y, base[x][y], prevG && x === prevG.x && y === prevG.y);
    const pv = (marker.cand.pv && marker.cand.pv.length) ? marker.cand.pv : [marker.cand.move];
    drawPvSequence(ctx, base, cur.color, pv);
}

export function showCandTooltip(marker, e) {
    const c  = marker.cand;
    const wr = typeof c.winrate === 'number' ? wrPct1(c.winrate).toFixed(1) + '%' : '-';
    let score = '-';
    if (typeof c.scoreLead === 'number')
        score = c.scoreLead >= 0
            ? `흑 +${c.scoreLead.toFixed(1)}집`
            : `백 +${Math.abs(c.scoreLead).toFixed(1)}집`;
    tooltip.innerHTML = `<b>${marker.rank}순위 · ${c.move}</b><br>흑 승률 ${wr}<br>예상 형세 ${score}`;
    tooltip.style.display = 'block';
    positionCandTooltip(e);
}

export function positionCandTooltip(e) {
    const rect = canvas.getBoundingClientRect();
    const w = tooltip.offsetWidth || 160, h = tooltip.offsetHeight || 60;
    let x = rect.right + 12;
    let y = rect.top;
    if (x + w > window.innerWidth - 4) { x = rect.left; y = rect.bottom + 10; }
    if (x < 4) x = 4;
    if (y + h > window.innerHeight - 4) y = window.innerHeight - h - 4;
    if (y < 4) y = 4;
    tooltip.style.left = x + 'px';
    tooltip.style.top  = y + 'px';
}

export function clearCandHover() {
    candState.hoveredIdx = -1;
    if (tooltip) tooltip.style.display = 'none';
    if (nav.currentIdx >= 0) showMove(nav.currentIdx);
}

/* ── mousemove 핸들러 (events.js 에서 canvas 에 등록) ── */
export function handleBoardMouseMove(e) {
    if (tryState.active) return;
    if (nav.currentIdx < 0 || candState.markers.length === 0) {
        if (candState.hoveredIdx !== -1) clearCandHover();
        return;
    }
    const { gx, gy } = mouseToGrid(e, canvas);
    const hit = candState.markers.findIndex(m => m.gx === gx && m.gy === gy);
    if (hit !== candState.hoveredIdx) {
        candState.hoveredIdx = hit;
        if (hit >= 0) {
            renderCandidateHover(candState.markers[hit]);
            showCandTooltip(candState.markers[hit], e);
        } else {
            if (tooltip) tooltip.style.display = 'none';
            showMove(nav.currentIdx);
        }
    } else if (hit >= 0) {
        positionCandTooltip(e);
    }
}

export function handleBoardMouseLeave() {
    if (candState.hoveredIdx !== -1) clearCandHover();
}
