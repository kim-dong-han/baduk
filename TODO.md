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
- **사활 위젯 타이젬 스타일 리디자인(waiting.html)**: 기존 밋밋한 위젯을 사활_01~03.png(타이젬) 형태로 전면 개편. ①패널 헤더("사활"+✕ 닫기)·서브헤더("오늘의 사활 (n/N)"+? 도움말, 우측 ●흑차례/○백차례 dot). ②난이도 pill 유지. ③나무 프레임 보드(.ts-board-wrap). ④정해 수순 돌 위 **번호 표기**(moveNums, draw()에서 흑=흰숫자/백=검은숫자). ⑤정답 시 **"정답입니다!" 축하 오버레이**(노란 팝 텍스트+🐼 마스코트 bob+확인 버튼) → 확인 시 **완료 도장**(.dimmed 어둡게+회전 ts-done-stamp)·재도전 pulse. 정답 보기(tsumeReveal)도 완료 상태로. ⑥하단 툴바 **‹이전 문제/🔍사활 힌트/재도전/다음 문제›** — history[] 스택으로 이전·다음 탐색(fetchNewProblem/renderProblem), 재도전=현 문제 초기화. 브라우저 검증(분석 대기화면): 3상태(문제/정답축하/완료도장) 모두 스크린샷과 일치. ※템플릿만 변경 → build 복사로 반영(재시작 불필요)
- **로그인 게이트(구글 OAuth2 실연동 + 데모 병행)**: 메인 진입 전 로그인 강제. **Spring Security 7 / Boot 4** — build.gradle에 `starter-security`+`starter-oauth2-client`. `config/SecurityConfig`: 정적·/login·/demo-login만 permitAll, 나머지 authenticated, 미인증→/login 리다이렉트, formLogin·CSRF off(로컬 단일+기존 fetch POST 토큰 없음), 로그아웃 POST /logout→/login?logout. 구글: env `GOOGLE_CLIENT_ID`/`SECRET` 둘 다 있으면 oauth2Login 활성(리다이렉트 URI /login/oauth2/code/google), 없으면 구글 버튼도 데모. `controller/AuthController` POST /demo-login(username|provider)→UsernamePasswordAuthenticationToken 세션 주입→/. `templates/login.html`(한게임 스타일 히어로+카드, 소셜 5종=구글·네이버·애플·페이코·페북). 7개 템플릿 topnav 우측에 로그아웃 폼(.logout-btn, common.css). **패키지 주의**(Boot4/Sec7 이동): PathRequest=boot.security.autoconfigure.web.servlet, CommonOAuth2Provider=security.config.oauth2.client. 검증(브라우저): /game→/login 리다이렉트, 네이버 데모(provider=네이버 UTF-8)→/game 입장, 로그아웃→/login?logout. ※Java 변경→재시작 필요
- **추천수 승률순 정렬 + 놓아보기 단순화**: ①후보수 top3를 KataGo 방문순(원 moveInfos 순서)이 아니라 **둘 쪽 승률 높은 순**으로(SingleGameService.buildOneMoveDetail: moveInfos를 playerIsBlack?winrate:1-winrate 로 내림차순, 동률 시 visits↓). 방문순엔 거의 안 둔 저평가수(예: 90수 4% O19)가 top3에 끼던 걸 제거. **최선수(bestMove)/변화도는 KataGo 1순위 유지** → 진 국면에선 1위 후보(생승률 최고)와 최선수가 근소하게 다를 수 있음(예: 90수 1위 S19 25.8% vs 최선수 T16 25.3%). 재분석해야 반영(기존 저장분은 구 순서). ②놓아보기: 검토중 배지·나가기 버튼·tryToolbar·reviewingTimer/애니 CSS 전부 제거, **btnTry 하나로 토글**(활성 시 텍스트 '놓아보기 종료'), 되돌리기는 기존 ‹. 검증: 재분석(a2db0865) 후 10·50·90수 승률 내림차순 확인, 놓아보기 클릭 시 배지 없이 버튼만 토글. ※Java 변경 → 재시작 필요
- **적응형 2차 정밀 분석(실수·악수만 고visits 재분석)**: 1차 전수(analysis-visits=200)로 빠르게 스캔 → 실수·악수(집손해≥3)로 잡힌 국면만 2차로 고visits(deep-visits=1500) 재분석해 저visits 오판 교정. 전 수 고visits 낭비 없이 정확도↑. **구현**: application.yaml `deep-visits:1500`(≤analysis-visits면 2차 생략). KataGoService `getDeepVisits()`+`analyzeTurnsAt(moves,turns,visits,own)`+`runTurnAnalysis()`(analyzeAllMoves도 이걸 사용하도록 일반화). SingleGameService: buildMoveDetails→`buildOneMoveDetail(moves,i,before,after,deep)` 분리, analyze()에서 1차 후 실수·악수의 turn i·i+1 union을 deepTurns로 모아 analyzeTurnsAt→해당 MoveDetail만 재생성(byTurn에 deep 노드 덮어씀), top3·구간·소요시간은 2차 후 계산. DTO: SingleGameResult `deepVisits`·`deepMoveCount`, MoveDetail `deepAnalyzed`. result.html: 메타헤더 "🎯 실수·악수 N수 1500 visits 정밀" 배지, moveInfo 등급 옆 🎯정밀재분석 배지(.deep-badge). **검증(1224142809.sgf 재분석, 128초, 9수 정밀)**: 90수 악수 12.1집→**최선 0집**(200v가 형세 오판해 최선수를 판 최대 악수·패착으로 오지목한 걸 교정), 100수 실수→보통, 92수 악수11.7→7.1, 뚜렷한 패착 없음으로 정정. 진짜 실수·악수(70·71·73·78·102)는 유지. ※Java 변경 → 재시작 필요
- **복기판 UI 정돈(바둑판 외 요소 정리)**: ①놓아보기 툴바 통일 — 되돌리기·처음으로 버튼 제거, 기존 `‹`(이전 수)/`←`로 놓은 돌 한 수씩 되돌림(tryStones 없으면 exitTryMode). 툴바=검토중 배지+안내문+나가기. tryReset 삭제. ②추천 히트맵 버튼 제거 → 기본 추천수 표시에 통합: 후보 마커가 항상 승률 색+% 원반(drawCandidateHeat), winrate 없는 구 데이터만 순위 번호 fallback. showHeatmap/toggleHeatmap/btnHeat 삭제. ③형세변화·승률변화·전체 수 분석(그래프+표) 전부 제거 — "바둑판 위의 수가 아님". area-chart/area-table 그리드영역·HTML·scoreChart/winrateChart·cursorPlugin/losingPlugin·setFilter/toggleSortByLoss·selected-row 하이라이트·관련 CSS 삭제. review-grid는 phases/board+side 2행만, 반응형 정돈(빈공간 제거). aiStatChart(AI 착수 통계)·패착 카드·핵심 실수는 유지, chart.min.js 유지. 브라우저 검증: 페이지 하단 깔끔, 콘솔 에러 없음. ※템플릿만 변경 → build 복사로 반영(재시작 불필요)

## 주의/미해결
- 기존 저장 JSON 일부 구 등급(S/A/B/C/D) → 재분석해야 새 등급 반영.
