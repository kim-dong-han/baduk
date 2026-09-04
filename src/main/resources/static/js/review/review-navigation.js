/* ════════════════════════════════════════════════════════════
   review-navigation.js — 수 표시·이동·비교·다시두기
   showMove 가 화면 렌더링의 중심 함수.
   ════════════════════════════════════════════════════════════ */

import { moves, nav, candState, losingMoveIdx, tryState } from './review-state.js';
import { BOARD_PX, gtpToGrid, wrPct1, wrPctI, px, py, CELL } from './review-utils.js';
import {
    buildBoardState, drawBoard, drawStone, drawOwnership,
    drawVariation, drawPvSequence,
    drawCandidateHeat, drawCandidateMarker, drawPlayedWinrate,
} from './review-board.js';
import { drawFlowChart } from './review-chart.js';

const canvas = document.getElementById('boardCanvas');

/* ── 메인 렌더 ── */

export function showMove(idx) {
    if (idx < 0 || idx >= moves.length) return;
    nav.currentIdx = idx;

    const ctx = canvas.getContext('2d');
    drawBoard(ctx);
    candState.markers = [];

    const cur = moves[idx];
    const variationActive = nav.showVariation && cur.bestPv && cur.bestPv.length > 0
                         && cur.move !== cur.bestMove;

    if (variationActive) {
        // 현재 수 두기 직전 국면 위에 최선수 변화도 표시
        const base  = buildBoardState(idx - 1);
        const prevG = idx > 0 ? gtpToGrid(moves[idx - 1].move) : null;
        for (let x = 0; x < 19; x++)
            for (let y = 0; y < 19; y++)
                if (base[x][y]) drawStone(ctx, x, y, base[x][y], prevG && x === prevG.x && y === prevG.y);
        drawVariation(ctx, base, cur);
    } else {
        const board = buildBoardState(idx);
        if (nav.showOwnership && cur.ownership && cur.ownership.length === 361)
            drawOwnership(ctx, cur.ownership);

        const lastG = gtpToGrid(cur.move);
        // 방금 둔 수가 추천수였는지 판정 → 그렇다면 금색 승률%로 착수 표시 대체
        let playedMatchWr = null;
        if (!nav.showSequence && lastG && board[lastG.x][lastG.y] === cur.color
                && cur.candidates && cur.candidates.length) {
            const match = cur.candidates.find(c => c.move === cur.move && typeof c.winrate === 'number');
            if (match) playedMatchWr = cur.color === 'B' ? match.winrate : 1 - match.winrate;
        }
        for (let x = 0; x < 19; x++)
            for (let y = 0; y < 19; y++)
                if (board[x][y])
                    drawStone(ctx, x, y, board[x][y],
                        lastG && x === lastG.x && y === lastG.y && playedMatchWr === null);

        // AI 추천수 상위 3개
        const candList = (cur.candidates && cur.candidates.length > 0)
            ? cur.candidates
            : ((cur.topMoves && cur.topMoves.length > 0)
                ? cur.topMoves.map(m => ({ move: m }))
                : (cur.bestMove ? [{ move: cur.bestMove }] : []));
        candList.forEach((c, i) => {
            if (!c.move || c.move === cur.move) return;
            const g = gtpToGrid(c.move);
            if (g && !board[g.x][g.y]) {
                if (typeof c.winrate === 'number') drawCandidateHeat(ctx, g.x, g.y, c, cur.color);
                else drawCandidateMarker(ctx, g.x, g.y, i + 1);
                if (typeof c.winrate === 'number')
                    candState.markers.push({ gx: g.x, gy: g.y, rank: i + 1, cand: c });
            }
        });

        if (playedMatchWr !== null) drawPlayedWinrate(ctx, lastG.x, lastG.y, cur.color, playedMatchWr);

        // 수순번호 표시
        if (nav.showSequence) {
            ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
            for (let i = 0; i <= idx; i++) {
                const g = gtpToGrid(moves[i].move);
                if (!g || !board[g.x][g.y]) continue;
                const num = String(i + 1);
                const fontSize = num.length >= 3 ? CELL * 0.26 : CELL * 0.32;
                ctx.font = `bold ${fontSize}px sans-serif`;
                ctx.fillStyle = moves[i].color === 'B' ? 'rgba(255,255,255,0.9)' : 'rgba(0,0,0,0.75)';
                ctx.fillText(num, px(g.x), py(g.y));
            }
        }
    }

    _updateNavButtons(idx, cur);
    _renderMoveInfo(cur, idx, variationActive);
    _renderWinrateBar(cur);
    drawFlowChart();
}

/* ── 내비게이션 ── */

export function prevMove() {
    if (tryState.active) {
        // 놓아보기 중엔 ← 으로 한 수 되돌리기
        if (tryState.stones.length) {
            // tryUndo 는 review-try.js — import 순환을 피해 직접 호출하지 않고
            // tryState 를 통해 이벤트로 처리 (events.js 에서 연결)
            _tryUndoCallback?.();
        } else {
            _exitTryCallback?.();
        }
        return;
    }
    showMove(nav.currentIdx - 1);
}
export function nextMove()          { showMove(nav.currentIdx + 1); }
export function skipMove(delta)     { showMove(Math.max(0, Math.min(moves.length - 1, nav.currentIdx + delta))); }
export function jumpToMove(idx)     {
    showMove(idx);
    document.getElementById('boardCanvas').scrollIntoView({ behavior: 'smooth', block: 'start' });
}
export function jumpToInput() {
    const val = parseInt(document.getElementById('moveInput').value);
    if (!isNaN(val) && val >= 1 && val <= moves.length) showMove(val - 1);
}

export function toggleSequence() {
    nav.showSequence = !nav.showSequence;
    document.getElementById('btnSeq').classList.toggle('active', nav.showSequence);
    if (nav.currentIdx >= 0) showMove(nav.currentIdx);
}
export function toggleVariation() {
    nav.showVariation = !nav.showVariation;
    document.getElementById('btnVar').classList.toggle('active', nav.showVariation);
    if (nav.currentIdx >= 0) showMove(nav.currentIdx);
}
export function toggleOwnership() {
    nav.showOwnership = !nav.showOwnership;
    document.getElementById('btnTerr').classList.toggle('active', nav.showOwnership);
    if (nav.currentIdx >= 0) showMove(nav.currentIdx);
}

/* 놓아보기 모듈 콜백 (try.js 가 setTryCallbacks() 로 주입) */
let _tryUndoCallback = null;
let _exitTryCallback = null;
export function setTryCallbacks(undoCb, exitCb) {
    _tryUndoCallback = undoCb;
    _exitTryCallback = exitCb;
}

/* ── 나란히 비교 ── */

function drawCompareBoard(canvasEl, pv) {
    canvasEl.width = BOARD_PX; canvasEl.height = BOARD_PX;
    const ctx = canvasEl.getContext('2d');
    drawBoard(ctx);
    const base  = buildBoardState(nav.currentIdx - 1);
    const prevG = nav.currentIdx > 0 ? gtpToGrid(moves[nav.currentIdx - 1].move) : null;
    for (let x = 0; x < 19; x++)
        for (let y = 0; y < 19; y++)
            if (base[x][y]) drawStone(ctx, x, y, base[x][y], prevG && x === prevG.x && y === prevG.y);
    drawPvSequence(ctx, base, moves[nav.currentIdx].color, pv);
}

function cmpStat(wr, score) {
    const w = wrPct1(wr).toFixed(1);
    const s = score >= 0 ? `흑 +${score.toFixed(1)}집` : `백 +${Math.abs(score).toFixed(1)}집`;
    return `흑 승률 <b>${w}%</b> · 예상 형세 <b>${s}</b>`;
}

export function openCompare() {
    if (nav.currentIdx < 0) return;
    const cur = moves[nav.currentIdx];
    if (!cur.bestMove || cur.move === cur.bestMove) return;

    const actualPv = moves.slice(nav.currentIdx, nav.currentIdx + 6).map(m => m.move);
    const bestPv   = (cur.bestPv && cur.bestPv.length) ? cur.bestPv : [cur.bestMove];
    drawCompareBoard(document.getElementById('cmpCanvasL'), actualPv);
    drawCompareBoard(document.getElementById('cmpCanvasR'), bestPv);

    const best = (cur.candidates && cur.candidates.length) ? cur.candidates[0] : null;
    const bWr  = best ? best.winrate   : cur.winrateBefore;
    const bScr = best ? best.scoreLead : cur.scoreLeadBefore;
    document.getElementById('cmpHeading').textContent = `${cur.turnNumber}수 비교 (${cur.color === 'B' ? '흑' : '백'})`;
    document.getElementById('cmpTitleL').textContent  = `내가 둔 수 · ${cur.move}`;
    document.getElementById('cmpTitleR').textContent  = `AI 최선수 · ${cur.bestMove}`;
    document.getElementById('cmpStatL').innerHTML = cmpStat(cur.winrateAfter, cur.scoreLeadAfter);
    document.getElementById('cmpStatR').innerHTML = cmpStat(bWr, bScr);
    document.getElementById('cmpPvL').textContent = '이후 실제 진행: ' + actualPv.slice(0, 6).join(' → ');
    document.getElementById('cmpPvR').textContent = '예상 진행: ' + bestPv.slice(0, 6).join(' → ');
    document.getElementById('cmpDiff').textContent = cur.scoreLoss > 0
        ? `이 수로 최선수 대비 약 ${cur.scoreLoss.toFixed(1)}집을 손해봤어요.`
        : '최선수에 가까운 좋은 수였어요.';
    document.getElementById('compareModal').style.display = 'flex';
}
export function closeCompare() { document.getElementById('compareModal').style.display = 'none'; }

/* ── 이 수부터 AI와 다시 두기 ── */

export function replayFromHere() {
    const idx = nav.currentIdx < 0 ? 0 : nav.currentIdx;
    const setup = moves.slice(0, idx).map(m => [m.color, m.move]);
    const payload = { moves: setup, userColor: moves[idx].color, fromTurn: moves[idx].turnNumber };
    sessionStorage.setItem('replaySetup', JSON.stringify(payload));
    location.href = '/play';
}

/* ── DOM 업데이트 헬퍼 (private) ── */

function _updateNavButtons(idx, cur) {
    document.getElementById('moveCounter').textContent = `${idx + 1} / ${moves.length}수`;
    document.getElementById('btnFirst').disabled = idx === 0;
    document.getElementById('btnPrev5').disabled = idx === 0;
    document.getElementById('btnPrev').disabled  = idx === 0;
    document.getElementById('btnNext').disabled  = idx === moves.length - 1;
    document.getElementById('btnNext5').disabled = idx === moves.length - 1;
    document.getElementById('btnLast').disabled  = idx === moves.length - 1;
    document.getElementById('btnCmp').disabled   = !(cur.bestMove && cur.move !== cur.bestMove);
}

function _renderMoveInfo(cur, idx, variationActive) {
    const lossColor = cur.scoreLoss >= 5 ? '#e74c3c'
                    : cur.scoreLoss >= 3 ? '#e67e22'
                    : cur.scoreLoss <= 0 ? '#2ecc71' : '#555';
    const lossText  = cur.scoreLoss > 0  ? `-${cur.scoreLoss}집`
                    : cur.scoreLoss < 0  ? `+${Math.abs(cur.scoreLoss)}집 (득점)` : '0집';

    const moverFrac  = f => (cur.color === 'B' ? f : 1 - f);
    const hasWrAfter  = typeof cur.winrateAfter  === 'number';
    const hasWrBefore = typeof cur.winrateBefore === 'number';
    const myPct   = hasWrAfter  ? wrPct1(moverFrac(cur.winrateAfter))  : null;
    const bestPct = hasWrBefore ? wrPct1(moverFrac(cur.winrateBefore)) : null;

    let rankTag = null, tagColor = '#888';
    if (cur.bestMove && cur.move === cur.bestMove) {
        rankTag = '✅ 최선수'; tagColor = '#27ae60';
    } else if (cur.candidates && cur.candidates.length) {
        const ci = cur.candidates.findIndex(c => c.move === cur.move);
        rankTag  = ci >= 0 ? `추천 ${ci + 1}순위` : '추천 밖';
        tagColor = ci >= 0 ? '#2980b9' : '#e67e22';
    }
    const rankBadge = rankTag
        ? ` <span style="font-size:11px;font-weight:700;color:${tagColor};background:${tagColor}1a;padding:2px 7px;border-radius:99px;">${rankTag}</span>`
        : ` <span style="font-size:11px;color:#aaa;">후보 비교 없음</span>`;
    const myMoveHtml = `<div class="info-row"><span class="info-label">내가 둔 수</span>`
        + `<span>${cur.move ? cur.move : '패스'}${myPct !== null ? ` · <b style="color:${lossColor};">${myPct.toFixed(1)}%</b>` : ''}${rankBadge}</span></div>`;

    let bestHtml = '';
    if (cur.bestMove && cur.move !== cur.bestMove) {
        bestHtml += `<div class="info-row"><span class="info-label">최선수</span><span style="color:#3498db;font-weight:700;">${cur.bestMove}${bestPct !== null ? ` · ${bestPct.toFixed(1)}%` : ''}</span></div>`;
        if (cur.bestPv && cur.bestPv.length > 1) {
            const pvText = cur.bestPv.slice(0, 6).join(' → ');
            bestHtml += `<div class="info-row" style="${variationActive ? '' : 'opacity:0.6;'}"><span class="info-label">예상 진행</span><span style="font-size:12px;color:#666;">${pvText}</span></div>`;
        }
    }

    document.getElementById('moveInfo').innerHTML = `
        <div class="info-row"><span class="info-label">수번</span><span><b>${cur.turnNumber}수</b> (${cur.color === 'B' ? '흑' : '백'})</span></div>
        ${myMoveHtml}
        <div class="info-row"><span class="info-label">집 손해</span><span style="color:${lossColor};font-weight:700;">${lossText}</span></div>
        <div class="info-row"><span class="info-label">등급</span><span class="grade-badge g-${cur.grade}">${cur.grade}</span>${cur.deepAnalyzed ? ' <span class="deep-badge" title="실수·악수 국면이라 고visits로 정밀 재분석한 수예요">🎯 정밀재분석</span>' : ''}</span></div>
        ${bestHtml}
        ${idx === losingMoveIdx ? '<div style="margin-top:8px;font-size:12px;color:#c0392b;font-weight:700;">🔑 이 수가 이 판의 패착이에요. 팽팽하던 승부가 여기서 기울었어요.</div>' : ''}
        ${variationActive ? '<div style="margin-top:8px;font-size:12px;color:#3498db;">바둑판에 표시된 번호는 최선수 이후 예상 진행입니다.</div>' : ''}
        ${nav.showOwnership && !variationActive ? '<div style="margin-top:8px;font-size:12px;color:#888;">■ 검정=흑 집 · □ 흰색=백 집 (칸이 클수록 확실). AI 예측 영역입니다.</div>' : ''}
        ${nav.showOwnership && variationActive ? '<div style="margin-top:8px;font-size:12px;color:#aaa;">변화도 표시 중엔 집 예측이 숨겨집니다.</div>' : ''}
        ${!variationActive && cur.candidates && cur.candidates.length ? '<div style="margin-top:8px;font-size:12px;color:#888;">바둑판의 ①②③에 마우스를 올리면 후보수의 승률·예상 진행을 볼 수 있어요.</div>' : ''}
    `;
}

function _renderWinrateBar(cur) {
    const blackWr = wrPct1(cur.winrateAfter);
    document.getElementById('wbBlack').style.width    = blackWr.toFixed(1) + '%';
    document.getElementById('wbBlackPct').textContent = '흑 ' + blackWr.toFixed(1) + '%';
    document.getElementById('wbWhitePct').textContent = (100 - blackWr).toFixed(1) + '% 백';

    const lead = cur.scoreLeadAfter;
    const sd   = document.getElementById('scoreDiff');
    if (lead >= 0) sd.innerHTML = `현재 형세: <span class="lead-b">흑 ${lead.toFixed(1)}집 유리</span>`;
    else           sd.innerHTML = `현재 형세: <span class="lead-w">백 ${Math.abs(lead).toFixed(1)}집 유리</span>`;

    const wrPct    = wrPct1(cur.winrateAfter);
    const sl       = cur.scoreLeadAfter;
    const scoreText = Math.abs(sl) < 0.1 ? '균형'
                    : sl > 0 ? `흑 +${sl.toFixed(1)}집 유리`
                              : `백 +${Math.abs(sl).toFixed(1)}집 유리`;
    document.getElementById('winrateBar').style.display   = '';
    document.getElementById('wrBarBlack').style.width     = wrPct.toFixed(1) + '%';
    document.getElementById('wrBlackVal').textContent     = `흑 ${wrPct.toFixed(1)}%`;
    document.getElementById('wrWhiteVal').textContent     = `백 ${(100 - wrPct).toFixed(1)}%`;
    document.getElementById('wrScoreText').textContent    = scoreText;
}
