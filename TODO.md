# TODO

> 단일 상태 소스(single source of truth). 진행 현황은 여기서만 관리.
> 작업 끝나면 진행중→완료로 이동(요약 1줄). 길어지면 완료 오래된 건 잘라낸다.

## 진행 중
- (없음)

## 방향 (2026-07-03 확정)
- **배포 중단** — 개인 GPU 연산 부담. 이 저장소=포트폴리오, 엔진 로컬, b28 고정. 배포판은 추후 별도 저장소. (PROJECT.md 프로젝트 성격 참조)
- 초점: **세부 페이지 콘텐츠 고도화**.

## 예정 / 후보 (페이지 콘텐츠, 우선순위 ⭐)
- **README 스크린샷 4장** (사용자 직접 촬영 필요) — `docs/images/` 가이드대로. 정적 데모/헤드라인용 히트맵은 pro `c9ba1000` 종반이 선명
- **GitHub Pages 켜기** (사용자 액션) — Settings→Pages 소스 main/docs. 정적 파일은 docs/에 준비됨
- `.card` box-shadow 통합은 HTML 공용 클래스 필요해 보수적 보류 유지

## 최근 완료 (최신순, 5건 유지)
- **추천수 승률순 정렬 + 놓아보기 단순화**: ①후보수 top3를 KataGo 방문순(원 moveInfos 순서)이 아니라 **둘 쪽 승률 높은 순**으로(SingleGameService.buildOneMoveDetail: moveInfos를 playerIsBlack?winrate:1-winrate 로 내림차순, 동률 시 visits↓). 방문순엔 거의 안 둔 저평가수(예: 90수 4% O19)가 top3에 끼던 걸 제거. **최선수(bestMove)/변화도는 KataGo 1순위 유지** → 진 국면에선 1위 후보(생승률 최고)와 최선수가 근소하게 다를 수 있음(예: 90수 1위 S19 25.8% vs 최선수 T16 25.3%). 재분석해야 반영(기존 저장분은 구 순서). ②놓아보기: 검토중 배지·나가기 버튼·tryToolbar·reviewingTimer/애니 CSS 전부 제거, **btnTry 하나로 토글**(활성 시 텍스트 '놓아보기 종료'), 되돌리기는 기존 ‹. 검증: 재분석(a2db0865) 후 10·50·90수 승률 내림차순 확인, 놓아보기 클릭 시 배지 없이 버튼만 토글. ※Java 변경 → 재시작 필요
- **적응형 2차 정밀 분석(실수·악수만 고visits 재분석)**: 1차 전수(analysis-visits=200)로 빠르게 스캔 → 실수·악수(집손해≥3)로 잡힌 국면만 2차로 고visits(deep-visits=1500) 재분석해 저visits 오판 교정. 전 수 고visits 낭비 없이 정확도↑. **구현**: application.yaml `deep-visits:1500`(≤analysis-visits면 2차 생략). KataGoService `getDeepVisits()`+`analyzeTurnsAt(moves,turns,visits,own)`+`runTurnAnalysis()`(analyzeAllMoves도 이걸 사용하도록 일반화). SingleGameService: buildMoveDetails→`buildOneMoveDetail(moves,i,before,after,deep)` 분리, analyze()에서 1차 후 실수·악수의 turn i·i+1 union을 deepTurns로 모아 analyzeTurnsAt→해당 MoveDetail만 재생성(byTurn에 deep 노드 덮어씀), top3·구간·소요시간은 2차 후 계산. DTO: SingleGameResult `deepVisits`·`deepMoveCount`, MoveDetail `deepAnalyzed`. result.html: 메타헤더 "🎯 실수·악수 N수 1500 visits 정밀" 배지, moveInfo 등급 옆 🎯정밀재분석 배지(.deep-badge). **검증(1224142809.sgf 재분석, 128초, 9수 정밀)**: 90수 악수 12.1집→**최선 0집**(200v가 형세 오판해 최선수를 판 최대 악수·패착으로 오지목한 걸 교정), 100수 실수→보통, 92수 악수11.7→7.1, 뚜렷한 패착 없음으로 정정. 진짜 실수·악수(70·71·73·78·102)는 유지. ※Java 변경 → 재시작 필요
- **복기판 UI 정돈(바둑판 외 요소 정리)**: ①놓아보기 툴바 통일 — 되돌리기·처음으로 버튼 제거, 기존 `‹`(이전 수)/`←`로 놓은 돌 한 수씩 되돌림(tryStones 없으면 exitTryMode). 툴바=검토중 배지+안내문+나가기. tryReset 삭제. ②추천 히트맵 버튼 제거 → 기본 추천수 표시에 통합: 후보 마커가 항상 승률 색+% 원반(drawCandidateHeat), winrate 없는 구 데이터만 순위 번호 fallback. showHeatmap/toggleHeatmap/btnHeat 삭제. ③형세변화·승률변화·전체 수 분석(그래프+표) 전부 제거 — "바둑판 위의 수가 아님". area-chart/area-table 그리드영역·HTML·scoreChart/winrateChart·cursorPlugin/losingPlugin·setFilter/toggleSortByLoss·selected-row 하이라이트·관련 CSS 삭제. review-grid는 phases/board+side 2행만, 반응형 정돈(빈공간 제거). aiStatChart(AI 착수 통계)·패착 카드·핵심 실수는 유지, chart.min.js 유지. 브라우저 검증: 페이지 하단 깔끔, 콘솔 에러 없음. ※템플릿만 변경 → build 복사로 반영(재시작 불필요)
- **AI 대국 형세 판단 + 계가**: /play. KataGoService.evaluatePosition(includeOwnership, maxVisits 300 → rootInfo.scoreLead·winrate·ownership361) + PlayService.estimate() + `POST /api/play/estimate`. play.html: 형세판단(btnEstimate)=현재 집차·흑승률 카드+집영역 오버레이(drawOwnershipPlay, +흑/−백 사각형, 돌 아래), 계가하기(btnScore)=같은 데이터 '흑/백 N집 승' 문구, 더블패스 종료 시 requestFinalScore 자동 계가(gameOverSub에 표시). estOwnership는 착수/패스/무르기/리셋 시 clearEstimate. 검증: 형세 "백 +0.3집·흑47%"+오버레이, 계가 "백 0.3집 승". ※Java 변경 → 재시작 필요
- **벤치마킹 4종(KaTrain·Lizzie·GRP) 기능 추가**: ①예상 기력(result.html, renderRankEstimate) — moves의 색별 평균 scoreLoss→rankBand로 흑/백 기력대 추정 헤드라인 배너(재미용 근사, 클라이언트). ②추천 히트맵(btnHeat) — 후보수를 ①②③ 대신 승률 색(winColorHsl, 초록=좋음)+% 원반으로, 둘 쪽 관점. ③주석 SGF 내보내기 — `GET /game/result/{id}/sgf`(SingleGameViewController)+SingleGameService.buildAnnotatedSgf(수번·등급·집손해·최선수 C[] 코멘트, CoordinateConverter.fromGtp→toSgfX/Y), 결과판 다운로드 링크. ④실시간 수 평가(KaTrain) — /play 착수마다 '최선 대비 -N집' 카드(moveEvalCard). KataGoService.getBestMoveEval(move+rootInfo.scoreLead 반환), PlayService.playUserMove가 착수前 최선lead와 착수後 lead 차로 손해 계산(+1 KataGo 호출/수, isBlack부호), getLastUserLoss→/api/play/move 응답 userLoss. 브라우저 검증: 기력배너·히트맵(57%초록/4%빨강)·SGF(4536B)·실시간(Q16최선/1선악수 -7.9집). ※Java 변경 → bootRun 재시작 필요

## 주의/미해결
- 기존 저장 JSON 일부 구 등급(S/A/B/C/D) → 재분석해야 새 등급 반영.
