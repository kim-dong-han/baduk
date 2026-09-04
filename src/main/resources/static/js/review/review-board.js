/* ════════════════════════════════════════════════════════════
   review-board.js — 바둑판 Canvas 렌더링 + 착점 상태 계산
   순수 Canvas 함수 + 보드 상태 계산만 담당.
   이벤트 등록 없음.
   ════════════════════════════════════════════════════════════ */

import { CELL, PAD, BOARD_PX, px, py, gtpToGrid, wrPct1, winColorHsl } from './review-utils.js';
import { moves } from './review-state.js';

/* ── 바둑 규칙 유틸 ── */

export function getAdjacent(x, y) {
    const adj = [];
    if (x > 0)  adj.push([x - 1, y]);
    if (x < 18) adj.push([x + 1, y]);
    if (y > 0)  adj.push([x, y - 1]);
    if (y < 18) adj.push([x, y + 1]);
    return adj;
}

export function getGroup(board, x, y) {
    const color = board[x][y], group = [], visited = new Set(), stack = [[x, y]];
    while (stack.length > 0) {
        const [cx, cy] = stack.pop();
        const key = cx * 19 + cy;
        if (visited.has(key)) continue;
        visited.add(key);
        if (board[cx][cy] !== color) continue;
        group.push([cx, cy]);
        for (const [nx, ny] of getAdjacent(cx, cy))
            if (!visited.has(nx * 19 + ny)) stack.push([nx, ny]);
    }
    return group;
}

export function hasLiberty(board, group) {
    for (const [x, y] of group)
        for (const [nx, ny] of getAdjacent(x, y))
            if (board[nx][ny] === null) return true;
    return false;
}

/** upToIdx 까지의 착점·따냄을 적용한 19×19 보드 상태를 반환 */
export function buildBoardState(upToIdx) {
    const board = Array.from({ length: 19 }, () => Array(19).fill(null));
    for (let i = 0; i <= upToIdx; i++) {
        const m = moves[i];
        const g = gtpToGrid(m.move);
        if (!g) continue;
        board[g.x][g.y] = m.color;
        const opp = m.color === 'B' ? 'W' : 'B';
        for (const [nx, ny] of getAdjacent(g.x, g.y)) {
            if (board[nx][ny] === opp) {
                const grp = getGroup(board, nx, ny);
                if (!hasLiberty(board, grp)) for (const [gx, gy] of grp) board[gx][gy] = null;
            }
        }
        const selfGrp = getGroup(board, g.x, g.y);
        if (!hasLiberty(board, selfGrp)) for (const [gx, gy] of selfGrp) board[gx][gy] = null;
    }
    return board;
}

/* ── Canvas 그리기 함수 ── */

/** 바둑판 배경 + 격자 + 화점 + 좌표 라벨 */
export function drawBoard(ctx) {
    const bg = ctx.createLinearGradient(0, 0, BOARD_PX, BOARD_PX);
    bg.addColorStop(0, '#E8B86D');
    bg.addColorStop(1, '#C9954A');
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, BOARD_PX, BOARD_PX);

    ctx.strokeStyle = 'rgba(0,0,0,0.5)';
    ctx.lineWidth = 0.8;
    for (let i = 0; i < 19; i++) {
        ctx.beginPath(); ctx.moveTo(px(i), py(0));  ctx.lineTo(px(i), py(18)); ctx.stroke();
        ctx.beginPath(); ctx.moveTo(px(0), py(i));  ctx.lineTo(px(18), py(i)); ctx.stroke();
    }
    const starCoords = [];
    [3, 9, 15].forEach(a => [3, 9, 15].forEach(b => starCoords.push([a, b])));
    ctx.fillStyle = 'rgba(0,0,0,0.65)';
    starCoords.forEach(([gx, gy]) => {
        ctx.beginPath(); ctx.arc(px(gx), py(gy), 3.5, 0, Math.PI * 2); ctx.fill();
    });

    const LABEL_COLS = 'ABCDEFGHJKLMNOPQRST';
    ctx.fillStyle = 'rgba(0,0,0,0.4)';
    ctx.font = '9px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    for (let i = 0; i < 19; i++) {
        ctx.fillText(LABEL_COLS[i], px(i), PAD - 14);
        ctx.fillText(LABEL_COLS[i], px(i), BOARD_PX - PAD + 14);
        ctx.fillText(String(i + 1), PAD - 15, py(i));
        ctx.fillText(String(i + 1), BOARD_PX - PAD + 15, py(i));
    }
}

/** 돌 그리기. isLast=true 이면 빨간 착수 표시 */
export function drawStone(ctx, gx, gy, color, isLast) {
    const cx = px(gx), cy = py(gy);
    const r = CELL * 0.44;
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
    if (color === 'B') {
        const g = ctx.createRadialGradient(cx - r * 0.32, cy - r * 0.32, r * 0.08, cx, cy, r);
        g.addColorStop(0, '#555'); g.addColorStop(1, '#000');
        ctx.fillStyle = g; ctx.fill();
    } else {
        const g = ctx.createRadialGradient(cx - r * 0.32, cy - r * 0.32, r * 0.08, cx, cy, r);
        g.addColorStop(0, '#fff'); g.addColorStop(1, '#ccc');
        ctx.fillStyle = g; ctx.fill();
        ctx.strokeStyle = '#888'; ctx.lineWidth = 0.8; ctx.stroke();
    }
    if (isLast) {
        ctx.beginPath(); ctx.arc(cx, cy, r * 0.42, 0, Math.PI * 2);
        ctx.strokeStyle = '#e74c3c'; ctx.lineWidth = 2; ctx.stroke();
    }
}

const CAND_BG    = ['#1a5276', '#2471a3', '#5dade2'];
const CAND_SIZES = [0.40, 0.36, 0.32];

/** 순위 번호 마커 (winrate 없는 구 데이터용) */
export function drawCandidateMarker(ctx, gx, gy, rank) {
    const cx = px(gx), cy = py(gy);
    const r = CELL * CAND_SIZES[rank - 1];
    ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.fillStyle = CAND_BG[rank - 1]; ctx.fill();
    ctx.strokeStyle = '#fff'; ctx.lineWidth = 1.5; ctx.stroke();
    const fs = Math.round(r * 1.25);
    ctx.font = `bold ${fs}px sans-serif`;
    ctx.fillStyle = '#fff';
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText(String(rank), cx, cy);
}

/** 승률 색 히트맵 후보 마커 (초록=좋음 / 빨강=나쁨, 둘 차례 기준) */
export function drawCandidateHeat(ctx, gx, gy, cand, playerColor) {
    const cx = px(gx), cy = py(gy), r = CELL * 0.46;
    const p = playerColor === 'B' ? cand.winrate : (1 - cand.winrate);
    ctx.save();
    ctx.globalAlpha = 0.72;
    ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.fillStyle = winColorHsl(p); ctx.fill();
    ctx.globalAlpha = 1;
    ctx.strokeStyle = 'rgba(255,255,255,0.85)'; ctx.lineWidth = 1.5; ctx.stroke();
    ctx.fillStyle = '#fff';
    ctx.font = `bold ${Math.round(CELL * 0.27)}px sans-serif`;
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText(wrPct1(p).toFixed(1) + '%', cx, cy);
    ctx.restore();
}

/** 추천수였던 착수 돌 위에 금색 승률% 표시 (착수 표시 겸용) */
export function drawPlayedWinrate(ctx, gx, gy, color, wr) {
    const cx = px(gx), cy = py(gy);
    ctx.save();
    ctx.font = `bold ${Math.round(CELL * 0.27)}px sans-serif`;
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    const txt = wrPct1(wr).toFixed(1) + '%';
    ctx.lineWidth = 3; ctx.lineJoin = 'round';
    ctx.strokeStyle = 'rgba(0,0,0,0.9)'; ctx.strokeText(txt, cx, cy);
    ctx.fillStyle = '#ffd23f'; ctx.fillText(txt, cx, cy);
    ctx.restore();
}

/** AI 집 예측 히트맵 (ownership 배열, +흑/-백) */
export function drawOwnership(ctx, ownership) {
    for (let gx = 0; gx < 19; gx++) {
        for (let gy = 0; gy < 19; gy++) {
            const v = ownership[(18 - gy) * 19 + gx];
            if (v == null) continue;
            const a = Math.abs(v);
            if (a < 0.15) continue;
            const side = CELL * 0.82 * a;
            ctx.fillStyle = v > 0
                ? `rgba(0,0,0,${(0.55 * a).toFixed(3)})`
                : `rgba(255,255,255,${(0.85 * a).toFixed(3)})`;
            ctx.fillRect(px(gx) - side / 2, py(gy) - side / 2, side, side);
        }
    }
}

/** 반투명 예상 진행 돌 (변화도·PV 표시용) */
export function drawGhostStone(ctx, gx, gy, color, num, isFirst) {
    const cx = px(gx), cy = py(gy);
    const r = CELL * 0.44;
    ctx.save();
    ctx.globalAlpha = 0.6;
    ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.fillStyle = color === 'B' ? '#1a1a1a' : '#f4f4f4'; ctx.fill();
    if (color === 'W') { ctx.strokeStyle = '#999'; ctx.lineWidth = 1; ctx.stroke(); }
    ctx.restore();
    if (isFirst) {
        ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2);
        ctx.strokeStyle = '#3498db'; ctx.lineWidth = 2.5; ctx.stroke();
    }
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    const fs = String(num).length >= 2 ? CELL * 0.34 : CELL * 0.4;
    ctx.font = `bold ${fs}px sans-serif`;
    ctx.fillStyle = color === 'B' ? '#fff' : '#222';
    ctx.fillText(String(num), cx, cy);
}

/** pv 수순을 baseBoard 위에 번호 매겨 반투명 돌로 표시 */
export function drawPvSequence(ctx, baseBoard, startColor, pv) {
    const board = baseBoard.map(col => col.slice());
    let color = startColor;
    const drawn = [];
    for (let k = 0; k < pv.length; k++) {
        const g = gtpToGrid(pv[k]);
        if (!g) { color = color === 'B' ? 'W' : 'B'; continue; }
        if (board[g.x][g.y]) { color = color === 'B' ? 'W' : 'B'; continue; }
        board[g.x][g.y] = color;
        const opp = color === 'B' ? 'W' : 'B';
        for (const [nx, ny] of getAdjacent(g.x, g.y)) {
            if (board[nx][ny] === opp) {
                const grp = getGroup(board, nx, ny);
                if (!hasLiberty(board, grp)) for (const [gx, gy] of grp) board[gx][gy] = null;
            }
        }
        drawn.push({ x: g.x, y: g.y, color, num: k + 1 });
        color = color === 'B' ? 'W' : 'B';
    }
    drawn.forEach(s => drawGhostStone(ctx, s.x, s.y, s.color, s.num, s.num === 1));
}

/** 최선수 이후 예상 진행 (변화도 토글) */
export function drawVariation(ctx, baseBoard, cur) {
    drawPvSequence(ctx, baseBoard, cur.color, cur.bestPv);
}
