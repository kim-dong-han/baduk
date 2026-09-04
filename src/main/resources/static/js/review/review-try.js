/* ════════════════════════════════════════════════════════════
   review-try.js — 놓아보기(시험 착수) 기능
   tryState 만 직접 수정. 다른 모듈은 tryState 를 읽기만.
   의존 방향: try → navigation (showMove, setTryCallbacks)
              try → board, state, utils
   ════════════════════════════════════════════════════════════ */

import { moves, nav, tryState } from './review-state.js';
import { gtpToGrid, gridToGtp } from './review-utils.js';
import {
    buildBoardState, getAdjacent, getGroup, hasLiberty,
    drawBoard, drawStone, drawCandidateHeat,
} from './review-board.js';
import { showMove, setTryCallbacks } from './review-navigation.js';

const canvas = document.getElementById('boardCanvas');

/* ── 내부 유틸 ── */

function tryBaseColor() {
    return (tryState.baseIdx >= 0 && moves[tryState.baseIdx])
        ? moves[tryState.baseIdx].color : 'W';
}

function tryNextColor() {
    const first = tryBaseColor() === 'B' ? 'W' : 'B';
    return (tryState.stones.length % 2 === 0) ? first : (first === 'B' ? 'W' : 'B');
}

function buildTryBoard() {
    const board = buildBoardState(tryState.baseIdx);
    for (const s of tryState.stones) {
        board[s.x][s.y] = s.color;
        const opp = s.color === 'B' ? 'W' : 'B';
        for (const [nx, ny] of getAdjacent(s.x, s.y)) {
            if (board[nx][ny] === opp) {
                const grp = getGroup(board, nx, ny);
                if (!hasLiberty(board, grp)) for (const [gx, gy] of grp) board[gx][gy] = null;
            }
        }
        const self = getGroup(board, s.x, s.y);
        if (!hasLiberty(board, self)) for (const [gx, gy] of self) board[gx][gy] = null;
    }
    return board;
}

export function renderTry() {
    const ctx  = canvas.getContext('2d');
    drawBoard(ctx);
    const board = buildTryBoard();
    const last  = tryState.stones.length
        ? tryState.stones[tryState.stones.length - 1]
        : (tryState.baseIdx >= 0 ? gtpToGrid(moves[tryState.baseIdx].move) : null);
    for (let x = 0; x < 19; x++)
        for (let y = 0; y < 19; y++)
            if (board[x][y]) drawStone(ctx, x, y, board[x][y], last && x === last.x && y === last.y);
    // AI 추천수 마커 — 빈 자리에만
    for (const m of tryState.recos)
        if (!board[m.gx][m.gy]) drawCandidateHeat(ctx, m.gx, m.gy, m.cand, tryState.recoSide);

    const nextKr = tryNextColor() === 'B' ? '흑' : '백';
    document.getElementById('moveCounter').textContent = `놓아보기 · ${tryState.stones.length}수 (다음: ${nextKr})`;
}

/* ── AI 분석 ── */

function tryMovesList() {
    const list = [];
    for (let i = 0; i <= tryState.baseIdx; i++)
        if (moves[i] && moves[i].move && moves[i].move.toLowerCase() !== 'pass')
            list.push([moves[i].color, moves[i].move]);
    for (const s of tryState.stones) list.push([s.color, gridToGtp(s.x, s.y)]);
    return list;
}

function setTryStatus(html) {
    const el = document.getElementById('moveInfo');
    if (el) el.innerHTML = `<div style="padding:4px 2px;">${html}</div>`;
}

export function analyzeTryPosition() {
    const token    = ++tryState.analyzeToken;
    const posMoves = tryMovesList();
    setTryStatus('<span style="color:#888;">AI 분석 중… <b>추천수</b>를 계산합니다</span>');

    fetch('/api/analyze/top', {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({ moves: posMoves }),
    })
    .then(r => r.json())
    .then(data => {
        if (token !== tryState.analyzeToken || !tryState.active) return;   // 최신 요청만 반영
        if (!data.ok) { setTryStatus('<span style="color:#c0392b;">분석 실패</span>'); return; }

        const side = data.sideToMove;
        tryState.recoSide = side;
        tryState.recos = (data.candidates || []).map((c, i) => {
            const g = gtpToGrid(c.move);
            if (!g) return null;
            const sideWr  = c.winrate / 100;
            const blackWr = side === 'B' ? sideWr : 1 - sideWr;
            return { gx: g.x, gy: g.y, rank: i + 1, cand: { move: c.move, winrate: blackWr, scoreLead: c.scoreLead } };
        }).filter(Boolean);

        _updateTryStatus(data);
        renderTry();
    })
    .catch(() => {
        if (token === tryState.analyzeToken)
            setTryStatus('<span style="color:#c0392b;">분석 오류</span>');
    });
}

function _updateTryStatus(data) {
    const side   = data.sideToMove;
    const sideKr = side === 'B' ? '흑' : '백';
    let html = '';

    if (tryState.stones.length) {
        const last     = tryState.stones[tryState.stones.length - 1];
        const playerKr = last.color === 'B' ? '흑' : '백';
        const playedWr = Math.min(99.9, Math.max(0.1, 100 - data.rootWinrate));
        let cmp = '';
        if (tryState.prevBest != null) {
            const diff = tryState.prevBest - playedWr;
            if (diff <= 2)
                cmp = ` <span style="color:#1e8449;font-weight:700;">· 최선급 👍</span>`;
            else if (diff < 8)
                cmp = ` <span style="color:#b7950b;font-weight:700;">· 최선보다 ${diff.toFixed(0)}%p 낮음</span>`;
            else
                cmp = ` <span style="color:#c0392b;font-weight:700;">· 최선보다 ${diff.toFixed(0)}%p 낮음 ⚠</span>`;
        }
        html += `<div style="font-size:14px;font-weight:800;margin-bottom:6px;">방금 둔 수 <b>${gridToGtp(last.x, last.y)}</b> (${playerKr}) → 승률 <b>${playedWr.toFixed(0)}%</b>${cmp}</div>`;
    }

    const best = (data.candidates && data.candidates.length)
        ? Math.min(99.9, Math.max(0.1, data.candidates[0].winrate)) : null;
    if (best != null)
        html += `<div style="font-size:13px;color:#555;">다음 <b>${sideKr}</b> 차례 · 추천수 최선 승률 <b>${best.toFixed(0)}%</b></div>`;
    html += `<div style="margin-top:8px;font-size:11px;color:#999;">판 위 색 원 = AI 추천수 (초록=좋음/빨강=나쁨), 숫자 = 그 자리에 두면 그 쪽 승률(%)</div>`;
    setTryStatus(html);
    tryState.prevBest = best;
}

/* ── 공개 API ── */

export function toggleTryMode() {
    if (tryState.active) { exitTryMode(); return; }
    if (nav.currentIdx < 0) nav.currentIdx = 0;
    tryState.active   = true;
    tryState.baseIdx  = nav.currentIdx;
    tryState.stones   = [];
    tryState.recos    = [];
    tryState.prevBest = null;
    const btn = document.getElementById('btnTry');
    btn.classList.add('active');
    btn.textContent = '놓아보기 종료';
    canvas.style.cursor = 'pointer';
    renderTry();
    analyzeTryPosition();
}

export function exitTryMode() {
    tryState.active   = false;
    tryState.stones   = [];
    tryState.recos    = [];
    tryState.prevBest = null;
    tryState.analyzeToken++;          // 진행 중 분석 응답 무시
    const btn = document.getElementById('btnTry');
    btn.classList.remove('active');
    btn.textContent = '놓아보기';
    canvas.style.cursor = '';
    showMove(tryState.baseIdx >= 0 ? tryState.baseIdx : 0);
}

export function tryUndo() {
    if (tryState.stones.length) {
        tryState.stones.pop();
        renderTry();
        analyzeTryPosition();
    }
}

/** Canvas click 에서 호출 — 착수 처리 + 자살수 방지 */
export function handleTryClick(gx, gy) {
    if (gx < 0 || gx > 18 || gy < 0 || gy > 18) return;
    if (buildTryBoard()[gx][gy]) return;   // 이미 돌이 있는 자리
    const color = tryNextColor();
    tryState.stones.push({ x: gx, y: gy, color });
    if (buildTryBoard()[gx][gy] !== color) { tryState.stones.pop(); return; }  // 자살수 금지
    tryState.recos = [];   // 새 국면 분석 전까지 이전 추천수 숨김
    renderTry();
    analyzeTryPosition();
}

/** main.js 초기화 시 호출 — prevMove 내부에서 쓸 콜백 등록 */
export function registerTryCallbacks() {
    setTryCallbacks(tryUndo, exitTryMode);
}
