#!/usr/bin/env bash
# 정적 데모(docs/) 재생성 스크립트.
# 실행 중인 로컬 서버(localhost:8081)의 렌더 결과를 스냅샷해 GitHub Pages용 정적 파일로 만든다.
# 사용: 서버를 띄운 상태에서  bash scripts/build-docs.sh
# index.html(랜딩/큐레이션 갤러리)은 손으로 유지하므로 이 스크립트가 덮어쓰지 않는다.
set -euo pipefail

BASE="http://localhost:8081"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DOCS="$ROOT/docs"

# 데모에 포함할 결과 id (내 기보 1 + 프로 2). 짧은 파일명 = id 앞 8자.
declare -A GAMES=(
  [6214e435]="6214e435-ac41-400f-95bd-8ba1792884f1"   # Shin Jinseo vs Kang Seungmin
  [c9ba1000]="c9ba1000-8654-4a74-aeca-b8bda6db5f2d"   # Choi Jeong vs Shin Jinseo (집예측 볼거리)
  [2525a1e9]="2525a1e9-406f-4fd4-ad98-2edae0853f64"   # 내 대국
)

mkdir -p "$DOCS/css" "$DOCS/js"
cp "$ROOT/src/main/resources/static/css/common.css" "$DOCS/css/"
cp "$ROOT/src/main/resources/static/js/chart.min.js" "$DOCS/js/"
touch "$DOCS/.nojekyll"

FILES=()
curl -s "$BASE/analysis/batch" -o "$DOCS/report.html"; FILES+=("$DOCS/report.html")
for short in "${!GAMES[@]}"; do
  curl -s "$BASE/game/result/${GAMES[$short]}" -o "$DOCS/result-$short.html"
  FILES+=("$DOCS/result-$short.html")
done

# 절대경로 → 상대경로, 백엔드 전용 링크는 데모 안으로, 재대국 버튼은 숨김
for f in "${FILES[@]}"; do
  sed -i \
    -e 's#class="brand" href="/analysis/batch"#class="brand" href="index.html"#g' \
    -e 's#/css/common.css#css/common.css#g' \
    -e 's#/js/chart.min.js#js/chart.min.js#g' \
    -e 's#href="/analysis/batch"#href="report.html"#g' \
    -e 's#href="/gallery"#href="index.html"#g' \
    -e 's#href="/notes"#href="index.html"#g' \
    -e 's#href="/play"#href="index.html"#g' \
    -e 's#href="/about"#href="index.html"#g' \
    -e 's#href="/game"#href="index.html"#g' \
    -e 's#id="btnReplay"#id="btnReplay" hidden#g' \
    "$f"
done

echo "docs/ 재생성 완료. index.html 은 수동 유지(카드 수치 갱신 필요 시 직접 수정)."
