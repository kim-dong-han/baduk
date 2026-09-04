/* ════════════════════════════════════════════════════════════
   review-report.js — 리포트 카드 렌더링
   (핵심 실수 · 패착 · 예상 기력 · 한 줄 총평 · 호수 · 학습 요약 · 총평 슬롯)
   ════════════════════════════════════════════════════════════ */

import { moves, keyMistakes, goodMoves, losingMove, losingMoveIdx, moverWr } from './review-state.js';
import { wrPctI } from './review-utils.js';
import { showMove, jumpToMove } from './review-navigation.js';

const $ = id => document.getElementById(id);

/* ── 핵심 실수 카드 ── */
export function renderKeyMistakes() {
    const list  = $('mistakesList');
    const count = $('mistakesCount');
    if (keyMistakes.length === 0) {
        count.style.display = 'none';
        list.innerHTML = '<div class="mistakes-empty">실수·악수로 분류된 수가 없습니다. 훌륭해요!</div>';
        return;
    }
    count.textContent = keyMistakes.length + '개';

    // 패착이 '보통'이라 실수·악수 목록에 없으면 끼워 넣는다
    const listMoves = keyMistakes.slice();
    if (losingMove && !listMoves.some(m => m.turnNumber === losingMove.turnNumber)) {
        listMoves.push(losingMove);
        listMoves.sort((a, b) => b.scoreLoss - a.scoreLoss);
    }
    list.innerHTML = listMoves.map((m, i) => {
        const isLosing    = losingMove && m.turnNumber === losingMove.turnNumber;
        const isDesperate = losingMove && !isLosing && m.color === losingMove.color
                         && m.turnNumber > losingMove.turnNumber;
        const tag = isLosing    ? '<span class="badge-losing">패착</span>'
                  : isDesperate ? '<span class="badge-desperate">승부수</span>' : '';
        return `<div class="mistake-card" data-jump="${m.turnNumber - 1}">
            <div class="mistake-rank">#${i + 1}</div>
            <div class="mistake-move">${m.turnNumber}수${tag}</div>
            <div class="mistake-spacer"></div>
            <div class="mistake-loss">-${m.scoreLoss}집</div>
            <span class="grade-badge g-${m.grade}">${m.grade}</span>
        </div>`;
    }).join('');
}

/* ── 패착 카드 ── */
export function renderLosingCard() {
    const card = $('losingCard');
    const el   = $('losingCallout');
    card.style.display = '';
    if (!losingMove) {
        el.innerHTML = '<div class="losing-desc">이 판은 뚜렷한 패착이 없어요 — 팽팽하게 끝났거나 승부가 서서히 기울었어요.</div>';
        return;
    }
    const side    = losingMove.color === 'B' ? '흑' : '백';
    const pre     = wrPctI(moverWr(losingMove, true));
    const post    = wrPctI(moverWr(losingMove, false));
    const bestTxt = (losingMove.bestMove && losingMove.bestMove !== losingMove.move)
        ? ` 최선수는 <b style="color:#3498db;">${losingMove.bestMove}</b>였어요.` : '';
    el.innerHTML =
        `<div class="losing-move-line">
            <span class="losing-turn">${losingMove.turnNumber}수</span>
            <span class="losing-stone ${losingMove.color}"></span>
            <span style="font-weight:700;">${side} ${losingMove.move}</span>
            <span class="grade-badge g-${losingMove.grade}">${losingMove.grade}</span>
         </div>
         <div class="losing-swing">이 수로 <b>${side} 승률 ${pre}% <span class="arrow">→</span> ${post}%</b> 로 무너졌어요.</div>
         <div class="losing-desc" style="margin-top:8px;">여기까지는 팽팽했지만 이 지점에서 승부가 한쪽으로 기울었어요.${bestTxt} 이후 ${side}의 실수·악수는 대부분 <b>승부수</b>예요.</div>
         <button class="losing-btn" data-move="${losingMoveIdx}">패착 국면 보기</button>`;
}

/* ── AI 착수 통계 ── */
const GRADES = ['최선', '좋음', '보통', '실수', '악수'];
function aiStats(color) {
    const ms      = moves.filter(m => m.color === color);
    const counts  = GRADES.map(g => ms.filter(m => m.grade === g).length);
    const matched = ms.filter(m => m.matchesBest).length;
    return { total: ms.length, counts, rate: ms.length ? matched / ms.length * 100 : 0 };
}
export function renderAiStats() {
    const blkStat = aiStats('B'), whtStat = aiStats('W');
    $('amrBlackPct').textContent  = blkStat.rate.toFixed(1) + '%';
    $('amrWhitePct').textContent  = whtStat.rate.toFixed(1) + '%';
    $('amrBlackFill').style.width = blkStat.rate + '%';
    $('amrWhiteFill').style.width = whtStat.rate + '%';

    new Chart($('aiStatChart').getContext('2d'), {
        type: 'bar',
        data: {
            labels: GRADES,
            datasets: [
                { label: '흑', data: blkStat.counts, backgroundColor: '#3a3a3a', borderRadius: 3 },
                { label: '백', data: whtStat.counts, backgroundColor: '#d0d0d0', borderColor: '#aaa', borderWidth: 1, borderRadius: 3 },
            ],
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'top', labels: { boxWidth: 12, font: { size: 12 } } },
                tooltip: { callbacks: { label: ctx => `${ctx.dataset.label}: ${ctx.raw}수` } },
            },
            scales: {
                x: { grid: { display: false }, ticks: { color: '#666', font: { size: 12 } } },
                y: { beginAtZero: true, ticks: { precision: 0, color: '#bbb', font: { size: 11 } }, grid: { color: 'rgba(0,0,0,0.05)' } },
            },
        },
    });
}

/* ── 예상 기력 ── */
function rankBand(avgLoss) {
    if (avgLoss < 0.6) return { label: '아마 최상위 · 프로급', sub: '약 5단 이상',  color: '#8e44ad' };
    if (avgLoss < 1.0) return { label: '아마 고단자',          sub: '약 1~4단',    color: '#2980b9' };
    if (avgLoss < 1.8) return { label: '아마 상급',            sub: '약 1~5급',    color: '#16a085' };
    if (avgLoss < 3.0) return { label: '아마 중급',            sub: '약 6~12급',   color: '#27ae60' };
    if (avgLoss < 5.0) return { label: '아마 초·중급',         sub: '약 13~18급',  color: '#e67e22' };
    return { label: '입문 · 초급', sub: '약 19급 이하', color: '#e74c3c' };
}
export function renderRankEstimate() {
    const el = $('rankEstimate');
    if (!el || !moves.length) return;
    const stat = { B: { loss: 0, n: 0, match: 0 }, W: { loss: 0, n: 0, match: 0 } };
    for (const m of moves) {
        const s = stat[m.color]; if (!s) continue;
        s.n++; s.loss += Math.max(0, m.scoreLoss || 0);
        if (m.bestMove && m.move === m.bestMove) s.match++;
    }
    const rowHtml = color => {
        const s = stat[color]; if (!s.n) return '';
        const avg      = s.loss / s.n;
        const b        = rankBand(avg);
        const matchPct = Math.round(s.match / s.n * 100);
        const disc     = color === 'B' ? '⚫ 흑' : '⚪ 백';
        return `<div class="re-row">
            <span>${disc}</span>
            <span class="re-band" style="color:${b.color};">${b.label}</span>
            <span class="re-sub">(${b.sub}) · 평균 실수 ${avg.toFixed(1)}집 · 최선율 ${matchPct}%</span>
        </div>`;
    };
    el.innerHTML = `<div class="re-head">🎯 예상 기력 <span style="font-weight:400;color:#a78bda;">— 이 대국 착수 정밀도 기반</span></div>
        ${rowHtml('B')}${rowHtml('W')}
        <div class="re-note">※ 정확한 급수가 아니라 이 한 판의 최선수 대비 정밀도로 계산한 재미용 근사치예요.</div>`;
    el.style.display = 'block';
}

/* ── 한 줄 총평 ── */
export function buildGameSummary() {
    const el = $('gameSummaryBody');
    if (!el || !moves.length) return;
    const jump = t => `<span class="gs-turn" data-move="${t - 1}" style="cursor:pointer;">${t}수</span>`;

    let last = null;
    for (let i = moves.length - 1; i >= 0; i--)
        if (typeof moves[i].scoreLeadAfter === 'number') { last = moves[i]; break; }

    const worst  = keyMistakes.length ? keyMistakes[0] : null;
    const best   = goodMoves.length   ? goodMoves[0]   : null;
    const parts  = [];

    if (last) {
        const lead = last.scoreLeadAfter;
        parts.push(Math.abs(lead) < 0.5
            ? `막판까지 <b>반집에 가까운 접전</b>이었습니다.`
            : `최종 형세는 <b>${lead > 0 ? '흑' : '백'} +${Math.abs(lead).toFixed(1)}집</b> 우세로 기울었습니다.`);
    }
    if (losingMove) {
        const s = losingMove.color === 'B' ? '흑' : '백';
        parts.push(`승부의 분기점은 ${jump(losingMove.turnNumber)} — 이 ${s}의 실착에서 균형이 무너졌습니다.`);
    } else if (worst) {
        const s = worst.color === 'B' ? '흑' : '백';
        parts.push(`가장 큰 실수는 ${jump(worst.turnNumber)}(${s}, <b>-${worst.scoreLoss.toFixed(1)}집</b>)였습니다.`);
    }
    if (best) {
        const s = best.color === 'B' ? '흑' : '백';
        parts.push(`반면 ${jump(best.turnNumber)}(${s})은 <b>+${Math.abs(best.scoreLoss).toFixed(1)}집</b>을 만든 이 판의 호수였습니다.`);
    }
    if (!parts.length) return;
    el.innerHTML = parts.join(' ');
    $('gameSummary').style.display = '';
}

/* ── 이 판의 호수 ── */
export function buildGoodMoves() {
    const card = $('goodMovesCard');
    const list = $('goodMovesList');
    if (!card || !list || !goodMoves.length) return;
    list.innerHTML = goodMoves.map(m => {
        const s = m.color === 'B' ? '⚫ 흑' : '⚪ 백';
        return `<div class="gm-item" data-jump="${m.turnNumber - 1}">
            <span class="gm-turn">${m.turnNumber}수</span>
            <span class="gm-meta">${s} · ${m.move || '패스'}${m.phase ? ' · ' + m.phase : ''}</span>
            <span class="gm-gain">+${Math.abs(m.scoreLoss).toFixed(1)}집 이득</span>
        </div>`;
    }).join('');
    card.style.display = '';
}

/* ── 총평 배너 슬롯 ── */
export function fillSummarySlots() {
    const last = moves.slice().reverse().find(m => m.scoreLeadAfter != null);
    const fin  = $('summaryFinal');
    if (fin && last) {
        const lead = last.scoreLeadAfter;
        fin.textContent = Math.abs(lead) < 0.5
            ? '반집 접전'
            : (lead > 0 ? '흑' : '백') + ' ' + Math.abs(lead).toFixed(1) + '집 우세';
    }
    const key = losingMove || (keyMistakes.length ? keyMistakes[0] : null);
    const el  = $('summaryKeyMove');
    if (el && key) {
        const side = key.color === 'B' ? '흑' : '백';
        el.classList.remove('none');
        el.textContent = key.turnNumber + '수(' + side + ') · -' + key.scoreLoss.toFixed(1) + '집';
        el.dataset.move = key.turnNumber - 1;
    } else if (el) {
        el.textContent = '큰 실착 없음';
    }
}

/* ── 학습 요약 ── */
function phaseStat(from, to) {
    const ms = moves.filter(m => m.turnNumber >= from && m.turnNumber <= to);
    if (!ms.length) return null;
    const hit  = ms.filter(m => m.bestMove && m.move === m.bestMove).length;
    const loss = ms.reduce((s, m) => s + Math.max(0, m.scoreLoss || 0), 0) / ms.length;
    const bad  = ms.filter(m => m.grade === '악수').length;
    return { n: ms.length, match: hit / ms.length * 100, loss, bad };
}
export function buildLessonCards() {
    const phases = [
        { name: '초반', s: phaseStat(1, 50) },
        { name: '중반', s: phaseStat(51, 150) },
        { name: '종반', s: phaseStat(151, 9999) },
    ].filter(p => p.s);
    if (!phases.length) return;

    const best  = phases.reduce((a, b) => b.s.match > a.s.match ? b : a);
    const worst = phases.reduce((a, b) => b.s.loss  > a.s.loss  ? b : a);
    const good  = [], next = [];

    good.push(`<b>${best.name}</b>에서 최선수 일치율이 ${best.s.match.toFixed(0)}%로 가장 높았습니다. (${best.s.n}수)`);
    const clean = phases.filter(p => p.s.bad === 0);
    if (clean.length) good.push(clean.map(p => p.name).join(' · ') + '에는 <b>악수가 한 번도 없었습니다</b>.');
    if (goodMoves.length) {
        const g = goodMoves[0];
        good.push(`${g.turnNumber}수는 <b>+${Math.abs(g.scoreLoss).toFixed(1)}집</b>을 만든 이 판의 호수였습니다.`);
    }
    if (good.length < 3) good.push('끝까지 한 판을 마무리한 것 자체가 복기의 출발점입니다.');

    next.push(`<b>${worst.name}</b>의 평균 집손해가 ${worst.s.loss.toFixed(2)}집으로 가장 컸습니다. 이 구간부터 다시 두어 보세요.`);
    if (losingMove) {
        const s = losingMove.color === 'B' ? '흑' : '백';
        next.push(`패착 <b>${losingMove.turnNumber}수(${s})</b>에서 균형이 무너졌습니다. AI 최선수 ${losingMove.bestMove || '-'}와 비교해 보세요.`);
    }
    const blunders = moves.filter(m => m.grade === '악수').length;
    if (blunders > 0) next.push(`악수로 분류된 수가 <b>${blunders}개</b>입니다. 오답노트에서 반복 패턴을 확인해 보세요.`);
    if (next.length < 3) next.push('한 수 두기 전에 후보수를 두 개 이상 비교하는 습관을 들여 보세요.');

    const put = (id, arr) => {
        const ul = $(id); if (!ul) return;
        ul.innerHTML = arr.slice(0, 3).map(t => `<li>${t}</li>`).join('');
    };
    put('lessonGood', good);
    put('lessonNext', next);
}

/* ── 리포트 클릭 이벤트 위임 (이 모듈에서 직접 등록) ── */
export function initReportEvents() {
    // 핵심 실수 카드
    $('mistakesList')?.addEventListener('click', e => {
        const card = e.target.closest('[data-jump]');
        if (card) jumpToMove(parseInt(card.dataset.jump));
    });
    // 호수 목록
    $('goodMovesList')?.addEventListener('click', e => {
        const item = e.target.closest('[data-jump]');
        if (item) jumpToMove(parseInt(item.dataset.jump));
    });
    // 한 줄 총평 수번 링크
    $('gameSummaryBody')?.addEventListener('click', e => {
        const span = e.target.closest('[data-move]');
        if (span) showMove(parseInt(span.dataset.move));
    });
    // 패착 카드 버튼
    $('losingCallout')?.addEventListener('click', e => {
        const btn = e.target.closest('[data-move]');
        if (btn) showMove(parseInt(btn.dataset.move));
    });
    // 총평 배너 승부처 클릭
    $('summaryKeyMove')?.addEventListener('click', e => {
        const el = $('summaryKeyMove');
        if (el?.dataset.move !== undefined) showMove(parseInt(el.dataset.move));
    });
}
