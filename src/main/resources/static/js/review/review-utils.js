/* ════════════════════════════════════════════════════════════
   review-utils.js — 좌표 변환 · 승률 포맷 순수 유틸
   다른 review 모듈을 import 하지 않는 leaf 모듈.
   ════════════════════════════════════════════════════════════ */

export const CELL     = 30;
export const PAD      = 28;
export const BOARD_PX = PAD * 2 + CELL * 18;

const GTP_COLS = 'ABCDEFGHJKLMNOPQRST';

/** GTP 좌표 문자열 → {x, y} 격자 (0-indexed). 변환 불가 시 null */
export function gtpToGrid(gtp) {
    if (!gtp || gtp === '' || gtp.toLowerCase() === 'pass') return null;
    const col = gtp[0].toUpperCase();
    let x = col.charCodeAt(0) - 65;
    if (x >= 8) x--;      // 'I' 없음
    const y = parseInt(gtp.substring(1)) - 1;
    if (isNaN(y) || x < 0 || x > 18 || y < 0 || y > 18) return null;
    return { x, y };
}

/** {x, y} 격자 → GTP 좌표 문자열 */
export function gridToGtp(x, y) { return GTP_COLS[x] + (y + 1); }

/** 격자 x → Canvas px */
export function px(gx) { return PAD + gx * CELL; }

/** 격자 y → Canvas py (y축 반전) */
export function py(gy) { return PAD + (18 - gy) * CELL; }

/** 승률(0~1) → 소수1자리 표시용 % (0.1~99.9 클램프) */
export function wrPct1(frac) { return Math.min(99.9, Math.max(0.1, frac * 100)); }

/** 승률(0~1) → 정수 표시용 % (1~99 클램프) */
export function wrPctI(frac) { return Math.min(99, Math.max(1, Math.round(frac * 100))); }

/** 둘 쪽 승률(0~1) → HSL 색상 (0%=빨강 → 100%=초록) */
export function winColorHsl(p) {
    const hue = Math.max(0, Math.min(120, p * 120));
    return `hsl(${hue.toFixed(0)}, 68%, 44%)`;
}

/** Canvas 마우스 이벤트 → 격자 {gx, gy} (반응형 배율 보정 포함) */
export function mouseToGrid(e, canvasEl) {
    const rect = canvasEl.getBoundingClientRect();
    const mx = (e.clientX - rect.left) * (BOARD_PX / rect.width);
    const my = (e.clientY - rect.top)  * (BOARD_PX / rect.height);
    const gx = Math.round((mx - PAD) / CELL);
    const gy = 18 - Math.round((my - PAD) / CELL);
    return { gx, gy };
}
