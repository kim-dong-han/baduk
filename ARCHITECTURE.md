# ARCHITECTURE

> 이 파일이 코드 탐색을 대체한다. 위치를 모를 때 Grep 하기 전에 여기를 먼저 본다.
> 구조가 바뀌면 코드와 함께 이 파일도 수정한다 (PR/커밋에 포함).

## 시스템 구조
```
브라우저(Thymeleaf)
   │  HTTP
Controller ── Service ── Parser/Util
                 │
            KataGoService ──(ProcessBuilder/stdin·stdout JSON, cwd=exe폴더)── katago_trt.exe(TensorRT/GPU)
                 │
            JSON 결과 파일 (C:/KataGo/GameResults/*.json)
```
- KataGo는 DB가 아니라 **로컬 subprocess + 파일 시스템**이 영속 계층이다.
- 분석 결과는 RDB가 아닌 `result-dir`의 `{uuid}.json`으로 저장/조회.

## 패키지 구조 (`com.example.badukanalyzer`)
```
controller/   요청 매핑만. 로직 없음.
  HomeController            /               (로그인 후 메인 허브 화면 home.html, 최근 분석 4건 주입)
  SingleGameViewController  /game/**        (화면, 비동기 분석)
  SingleGameController      /api/game/**    (REST)
  AnalysisController        /analysis/batch (배치 화면)
  AuthController            /login·/register·/login-local  (로그인 게이트·회원가입, remember-me 쿠키 발급)
  PlayController            /play(AI 대국)·/study(실시간 분석판)·/api/play/** + /api/analyze/top (무상태 국면 분석: 놓아보기·분석판 추천수)
  UploadController          /upload/gib     (업로드)
  TygemController           /tygem/**       (타이젬 연동)
  TsumegoController         /api/tsumego/** (대기 중 사활 문제)
service/      비즈니스 로직.
  SingleGameService    단일 기보 분석 파이프라인 (등급/구간/저장)
  KataGoService        subprocess 통신, 쿼리 생성, 진행률 콜백. getTopMoves(상위후보)·analyzeTop(root 승률+후보, 놓아보기용) — 모두 '둘 차례' 관점 winrate
  PlayService          AI 대국 상태(history)·힌트(getHints)·무상태 분석 패스(analyzeTop)
  AnalysisService      실력 리포트 집계 — 저장된 복기 결과(GameResults/*.json)를 내기보/프로(신진서 vs)·구간별로 합산(재분석 안 함)
  AnalysisJobStore     ConcurrentHashMap 기반 비동기 Job/진행률 (Job.fileName, getRunningJobs)
  TsumegoService       사활 문제 로드(@PostConstruct)·랜덤 제공, 인메모리 List
  GibService/SgfService, TygemCrawlerService, TygemFileWatcherService
parser/       GibParser, SgfParser → List<Move> / TsumegoSgfParser → TsumegoProblem
converter/    SgfConverter
util/         CoordinateConverter (SGF 좌표 ↔ GTP 좌표)
domain/       Move, Game, AnalysisResult
dto/          MoveDetail(bestPv 포함), SingleGameResult, AnalysisResponse, UploadResponse, TsumegoProblem
```
복기 화면(result.html)은 판 위 **마우스 휠로 이전/다음 수 이동**(위=이전, 아래=다음, 60ms 스로틀).

화면 템플릿: `resources/templates/home.html`(메인 허브), `game/{index,result,waiting,play,study}.html`, `analysis/{batch,notes,gallery}.html`, `about.html`
- **실시간 분석판(study.html)**: `/study`. AI 대국(play) 없이 빈 판에서 흑·백 직접 착수 → 매 수 `/api/analyze/top`으로 추천수 승률(둘 차례 관점) 실시간 표시. 무르기·패스·전체지우기. 백엔드 신규 없음(analyze/top 재사용). 판/따냄/마커 로직은 result.html 놓아보기와 동형. 레이아웃은 CSS Grid: ≥1151px `[판 | 우측패널 300px]`, ≤1150px `[요약줄 / 판 / 버튼]`(우측패널을 `display:contents`로 해체, **행은 전부 auto + `.play-layout{flex:none}`** — flex:1 로 두면 행이 눌려 판 위아래가 잘림). `computeLayout()`이 CELL·BOARD_PX를 매 렌더 재계산: 넓을 땐 board-area 가로·세로 중 작은 쪽, 쌓인 배치에선 가로 폭 기준+`innerHeight*0.74` 상한(행 높이로 재면 행=auto 라 순환). 최소 280·최대 1000px. nav 는 `topnav rail`(아이콘 62px, 호버 시 234px 펼침). **추천수 목록 카드는 제거** — 추천수는 판 위 색 원으로만 읽고, 원에 **호버하면 그 수의 PV(참고도)** 가 번호로 뜬다(`hoverPv`). 상태 문구는 판 아래 `#engineState`(`.board-hint`) 한 줄. **마우스 휠**: 위=무르기·아래=다시두기(`undone` 스택, 새 착수 시 초기화).
- **메인 허브(home.html)**: 로그인 후 `/` 진입 화면. 히어로 배너(사용자 인사+기보복기 CTA)+기능 타일 6종(실력리포트/기보복기/오답노트/갤러리/AI대국/소개)+최근 분석 4건 카드(`/game/result/{id}` 링크). 한게임 바둑 메인 포털 스타일. topnav 브랜드 로고=`/`(홈), 모든 페이지 topnav 첫 링크에 "홈" 추가.
- **로그인 유지(remember-me)**: 로컬 회원 로그인 시 14일(일반 웹사이트 표준) 서명 쿠키 발급(TokenBasedRememberMeServices, SHA256, 무상태). 서버 재시작·브라우저 종료 후에도 쿠키로 자동 재인증(AppUserDetailsService가 아이디로 계정 재로드, UserPrincipal=UserDetails 래퍼). 세션은 인메모리라 재시작 시 소멸하지만 쿠키가 커버. 구글·네이버 OAuth 로그인은 이 경로와 무관(세션 기반).
공통 CSS: `resources/static/css/common.css` — **앱 셸 = [사이드바 232px | 본문] 2컬럼 그리드**.
- 마크업은 `templates/fragments/shell.html` 하나만 쓴다(페이지별 `<nav>` 복사 폐지). 프래그먼트 3종:
  `side(active)` / `side_rail(active)`(아이콘 62px, 호버 시 펼침 — study 전용) / `topbar`(본문 상단 60px).
  각 페이지는 `<aside th:replace="~{fragments/shell :: side('키')}">` + `<main class="main">`(+`fill`=100vh, play·study) 구조.
  active 키: home|game|play|study|notes|gallery|batch|about.
- `body:has(> .side){display:grid; grid-template-columns:var(--sidebar-w) minmax(0,1fr)}`. `.side`는 `position:sticky; height:100vh`.
- **`.page`는 중앙정렬 컨테이너가 아니다** — `max-width:none; margin:0; padding:16px 20px 24px; flex-column + gap:var(--gap)`.
  (`.page.narrow`만 960px 중앙정렬 예외 — 로그인·회원가입·소개 같은 읽기 화면)
- 재사용 컴포넌트/그리드(페이지별로 다시 만들지 말 것): `.panel / .panel-hd / .panel-bd / .box`,
  `.g-2\/1`(266px+1fr) `.g-3` `.g-4` `.g-6` `.g-board`(342px+1fr).
- 토큰 추가: `--gap:14px`, `--border-soft`, `--sidebar-text/dim/line/card`. `--border`=#e4e8f0, `--sidebar-w`=232px.
- 반응형: ≤1280px 에서 `.g-6`→3열·`.g-4`→2열, ≤1080px 에서 사이드바가 상단 가로바로 눕고 모든 `.g-*`가 1열.

홈(home.html)은 4행 대시보드: ①히어로 ②바로가기 6열 ③`[좌 266px 최근분석+진행중 | 우 실력 리포트(바둑판 342px + 총평/승부흐름/기력·구간3열)]` ④지표 4열(AI 착수 통계·패착·핵심 실수 TOP3·이 판의 호수).
데이터는 컨트롤러 변경 없이 기존 `recent` 하나로 충당(`recent[0]`의 moves/top3Mistakes/top3GoodMoves/opening·middle·endgame). 데이터 없는 패널은 지우지 않고 "데이터 없음" 빈 상태로 렌더. 1440×900에서 4행이 스크롤 없이 보인다.
**주의**: 일반 `<script>` 안의 중첩 배열 리터럴(`[[x,y]]` 형태)과 HTML 주석은 Thymeleaf 인라인 표현식으로 해석돼 응답이 잘린다 → `th:inline="none"` 필수.
사활 문제: `resources/tsumego/*.sgf` (번들 75개=쉬움/보통/어려움 각 25. 파일 추가 시 자동 인식, 재시작 필요)

## 사활(Tsumego) 위젯 — 대기 중 학습
- 목적: 분석 대기(waiting.html) 동안 랜덤 사활 문제를 풀게 해 체감 대기시간↓.
- 로드: `TsumegoService.load()` `@PostConstruct`로 `classpath*:tsumego/*.sgf` 1회 파싱 → 인메모리.
- API: `GET /api/tsumego/random?difficulty=&exclude=`(없으면 204), `GET /api/tsumego/count`.
  - `difficulty`(쉬움/보통/어려움/전체·생략=전체) 필터, `exclude`=직전 문제 id 제외 → "다음 문제"가 항상 바뀜. 후보 없으면 단계적 완화(문제 수 제한 없음).
- `TsumegoProblem`: boardSize/stones(초기배치)/answers(정답 첫수,복수)/solution(정해)/region(코너 확대)/difficulty.
- 난이도 판정(TsumegoSgfParser.detectDifficulty): 파일명 접두사(쉬움_/보통_/어려움_ 또는 easy/normal/hard) → SGF `DIFF[]` → 정해 수순 길이(≥8 어려움/≥4 보통/그외 쉬움).
- **보드 크기 지원**: SGF `SZ[]`를 읽어 5~19로반 처리(좌표·region·화점 모두 크기 기준). 번들 문제는 5~11로 코너/소반 사활.
- 좌표는 전부 GTP. 정답 미추출 문제는 로드 시 건너뜀.
- **UI(타이젬 스타일, waiting.html)**: 패널 헤더(사활+✕ tsumeClose)·서브헤더(오늘의 사활 (n/N)+?도움말, 우측 흑/백차례 dot)·난이도 pill. 나무 프레임 보드, 정해 수순 돌 위 번호(moveNums). 정답 시 "정답입니다!" 축하 오버레이(🐼+확인)→확인 시 완료 도장(.dimmed+ts-done-stamp)·재도전 pulse. 정답 보기(tsumeReveal)도 완료 상태. 이전/다음 문제는 history[] 스택 탐색(fetchNewProblem/renderProblem), 재도전=현 문제 초기화.
- 번들 75개 출처(2026-08-03 교체): **101books.github.io**(공개 고전 사활, 퍼블릭 도메인) SGF. 책=난이도 매핑 — 쉬움=현현기경(2단)/보통=하시모토 명작·마에다(단급)/어려움=발양론(7단), 각 25개. 전부 19×19 코너·변 실전형(구 9×9 소반 잡기 대비 대폭 상향). 다운로드 시 루트노드에 `PL[색]`·`C[프롬프트]` 주입(파서가 SZ 없으면 19, PL 없으면 첫 수 색). 책 정해=메인라인 신뢰라 KataGo 검증 생략. ETL: 일회성 bash(git tree/contents API로 목록→shuf→raw 다운로드→prefix 저장).

## 데이터 흐름 (단일 기보 분석)
```
POST /game/analyze {fileName}
  → jobId 발급, redirect /game/waiting/{jobId}
  → SingleGameService.analyzeAsync() @Async 백그라운드
       parseFile → KataGoService.analyzeAllMoves(progressCallback)
       → buildMoveDetails (scoreLoss/winrateLoss/grade/phase)
       → saveResult({uuid}.json)
waiting.html 3초 폴링 GET /game/status/{jobId} → 완료 시 /game/result/{id}
```
- 분석 턴: 0, 50, 100, 마지막 20의 배수, 마지막 수 / maxVisits 20
- scoreLoss = `rootInfo.scoreLead`(최선 기대) − 실제 착점 scoreLead, max(0)
- 구간: 초반 ≤50, 중반 51~150, 종반 >150
- 등급: 최선<0.5 / 좋음<1.5 / 보통<3 / 실수<5 / 악수≥5 (집수 손실 기준)

## 인증 방식
- Spring Security 로그인 게이트. **모든 페이지 인증 필요**, `/login`·정적 리소스만 공개.
- `config/SecurityConfig`: 미인증 → `/login` 리다이렉트(LoginUrlAuthenticationEntryPoint). formLogin·CSRF 끔(로컬 단일 사용자 + 기존 fetch/폼 POST가 토큰 없음). 로그아웃 POST `/logout` → `/login?logout`.
- **소셜 OAuth2(구글·네이버)**: SecurityConfig에서 키 있는 provider만 등록(둘 다 있으면 둘 다, `oauth2Login` 활성). 키 주입=env 또는 루트 `google-oauth.properties`(gitignore, `spring.config.import: optional:file:...`).
  - **구글**: `GOOGLE_CLIENT_ID/SECRET` → CommonOAuth2Provider.GOOGLE, 리다이렉트 `/login/oauth2/code/google`.
  - **네이버**: `NAVER_CLIENT_ID/SECRET` → 수동 ClientRegistration(`naverRegistration()`: authorize/token/userinfo URI = nid.naver.com·openapi.naver.com, CLIENT_SECRET_POST, `userNameAttributeName="response"`), 리다이렉트 `/login/oauth2/code/naver`. 사용자정보가 `response{id,email,name,profile_image}`로 **중첩** → GlobalUserAdvice가 `response` 맵을 풀어 name/email/picture(profile_image) 읽음. 콘솔=developers.naver.com, Callback URL 등록 필요.
  - 없으면 해당 버튼은 데모. 발급 순서는 `google-oauth.properties.example`. 검증: 더미키로 `/oauth2/authorization/{google|naver}` → 302(accounts.google.com / nid.naver.com) 확인(실키는 사용자 발급).
- **아이디/비밀번호(로컬 계정)**: 회원가입한 실제 계정만. `service/UserAccountService`(파일 기반, `app.users-file` 기본 `C:/KataGo/users.json`, @PostConstruct 로드·인메모리 맵·BCrypt). `AuthController`: `GET/POST /register`(가입: 아이디≥3·영숫자_.-, 비번≥4·확인일치, 중복검사 → 저장 → `/login?registered`), `POST /login-local`(authenticate → 성공 시 principal=UserAccount 로 UsernamePasswordAuthenticationToken 세션 주입 → `/`, 실패 `/login?error`). 데모 로그인(`/demo-login`)은 제거됨. GlobalUserAdvice가 principal이 UserAccount면 displayName·email 표시. 화면: `templates/register.html`.
- **공용 상단 헤더**: `templates/fragments/appheader.html`(fragment `header(active)`) + `static/css/app-header.css`. 새 디자인 페이지(home·game)가 함께 쓴다. 브랜드·가로 내비 7개·계정 `<details>` 드롭다운(로그아웃 POST)·모바일 햄버거+내비, 토글 JS 는 fragment 안에 포함. **AI 대국은 하위 메뉴(`.nav-group`/`.nav-submenu`)** — AI 대국(`/play`)과 실시간 분석(`/study`)을 묶었고 hover/focus-within 으로 열린다(JS 없음). 부모 링크는 active 가 play·study 둘 다에서 켜지고, 하위 항목은 `.current` 로 표시. 모바일 내비에서는 실시간 분석이 `.sub` 로 한 단 들여쓰기된다. active 키: home|game|notes|gallery|play|batch|about|study. ※ 사이드바 셸(`fragments/shell.html`)은 이제 `/register` 등 일부만 사용 — 주요 10개 화면은 모두 이 헤더로 전환 완료.
- **바둑판 canvas 렌더 주의**: `/play`·`/study`·`/game/result` 의 판은 canvas 가 나무 바탕·격자·좌표를 **직접** 그린다. 따라서 `.board-area` 에 나무색 배경을 또 칠하면 색이 다른 판이 두 겹으로 보인다(2026-08-27 수정). `.board-area` 는 크기만 잡고(`aspect-ratio:1`), `#boardCanvas` 는 `width/height:100% !important` 로 그 정사각형을 정확히 채운다 — computeLayout() 이 잡는 BOARD_PX 가 영역보다 작아도 빈 띠가 남지 않는다. canvas 에 `border` 는 절대 주지 말 것 — 클릭 좌표가 `getBoundingClientRect()` 기준이라 테두리만큼 어긋난다(그림자·radius 는 안전).
- 화면: `templates/analysis/batch.html` — **실력 리포트(배치 분석)**. `app-header.css` + 전용 `static/css/batch.css`(아이보리+딥그린+바둑판 나무색, 전부 `.batch-page` 하위로 스코프, common.css 미사용). 읽는 순서: 인트로+기간 선택 → 히어로(한 줄 요약·평균 AI 일치율·직전 N판 대비 변화 + 실제 국면 캔버스) → 지표 4칸 → 실력 변화 추이 + 밸런스 레이더 → 구간별 분석(프로 기보 비교 포함) → 강점/반복 실수 → 최근 분석 기보 표 → 다음 학습 추천. **차트는 Canvas 직접 렌더**(외부 라이브러리 없음). 히어로 판은 집손해가 가장 컸던 실수 국면(`getUserMistakeNotes()` 1위의 `previewMoves`)을 그리고 그 자리를 빨간 원으로 표시. 기간 버튼(최근 30판/전체)은 서버 재요청 없이 이미 받은 판 목록을 자르고 지표·그래프·표를 다시 계산한다. 레이더 6축(초·중·종반 일치율 + 수 품질=excellentRate, 집 지키기=100-집손해×12, 큰 실수 없음=100-악수율×5)은 기존 집계에서 파생한 값. 백엔드 추가는 표시용뿐: `AnalysisService.getUserGameRows()`(→ `dto/BatchGameRow`, 저장된 결과만 읽어 판별 일치율·집손해·실수 수 계산, 오래된 순), 컨트롤러가 `userGames`·`focusNote`·`mistakeTotal`·`proMatchByPhase` 주입. 기존 `userResults`/`proResults`/`userGameCount`/`userWeaknesses`/`error`/`running` 모델과 `running` 시 5초 meta refresh 는 그대로.
- 화면: `templates/analysis/gallery.html` — **갤러리**. `app-header.css` + `static/css/gallery.css`. 사활 문제 75(번들 SGF, `TsumegoService.all()`)와 분석 기보 31(`GalleryItem`)을 한 그리드에 카드로. 썸네일은 전부 **실제 국면 캔버스**(사활=stones+region 확대, 기보=`previewMoves` 앞 80수로 최종 국면 계산). 필터=카테고리/난이도(사활 쉬움·보통·어려움)/정렬/출처, 검색·페이징·보기전환은 클라이언트. 카드 클릭: 기보→`/game/result/{id}`, 사활→모달(정해 수순 표시). 북마크는 localStorage.
- 화면: `templates/analysis/notes.html` — **오답노트**. `app-header.css` + `static/css/notes.css`. `MistakeNote`(실수·악수) 목록을 가로형 카드로. 썸네일은 `previewMoves`(그 수 직전까지)로 그린 국면에 **실수 자리 빨간 강조**. 왼쪽: 복습 상태 도넛(conic-gradient)·구간·심각도 필터. 복습 상태/횟수·북마크는 localStorage(3회 이상=완료). 복습하기 → `/game/result/{id}?move={turn}` 딥링크.
- 화면: `templates/about.html` — **소개**. `app-header.css` + `static/css/about.css`. 히어로(캔버스 판)·가치 5카드·KataGo 엔진(네트워크 비주얼+visits)·통계 4칸·가치 3칸·푸터. 통계는 `AboutController` 가 실제 값 주입(분석 기보 수·사활 수·오답 수·평균 일치율·visits).
- 화면: `templates/game/study.html` — **실시간 분석(연구용)**. `app-header.css` + 전용 `static/css/study.css`(웜 아이보리+딥그린, common.css 미사용). 3열 [분석 도구 190px | 바둑판 | 분석 대시보드]. 왼쪽: 무르기/패스/전체 지우기 + 집예측·좌표 토글, 현재 차례, 분석 설정(`studyVisits`·`komi` 모델 주입). 오른쪽: 승률 그래프(캔버스 직접 렌더, 외부 라이브러리 없음) → 예상 집 차이(+추이) → 수 품질 분포(도넛) → 추천수 TOP5 표(행 클릭=예상 진행) → 최근 승률 하락 TOP3. 하단: AI 코멘트(#standText/#lastText) + 국면 요약. **대시보드는 전부 기존 `/api/analyze/top` 응답만으로 만든다** — 착수할 때마다 [수번·흑 승률·흑 기준 집차]를 클라이언트에 누적하고(무르기 시 뒤를 잘라냄), 수 품질은 직전 국면 최선 집차 − 실제 집차 = 집손해를 SingleGameService.calcGrade 와 **같은 임계값**(0.5/1.5/3/5)으로 등급화. 기존 판 JS(캔버스·추천수 원반·참고도 호버·휠 이동·집예측)는 유지하고 5곳만 보강(좌표 토글 플래그, 집예측 버튼→토글 스위치, 상태 배지 연동, updateStatus 훅, clearAll 초기화).
- 화면: `templates/game/play.html` — **AI 대국**. `app-header.css` + 전용 `static/css/play.css`(아이보리+세이지, common.css 미사용). [왼쪽 바둑판 카드 | 오른쪽 AI 패널 390px] 2열. 왼쪽: 대국자(흑/VS/백) → `.board-area`(aspect-ratio 1, 최대 720px) 안의 `#boardCanvas` + 색 선택 오버레이(`#setupSection`) → `#gameSection`(차례 카드·수순·무르기/패스/기권). 오른쪽: 생각 중 → 현재 형세(`#wrBar`) → AI 추천수 TOP5(`#hintCard`, 버튼 요청식) → AI 한마디(착수 평가·형세 판단) → 대국 정보. 종료 시 `#gameOverSection` 이 **모달**로 뜬다(CSS 기본 flex + 인라인 none → 기존 style.display 토글 그대로 동작). 기존 대국 JS(약 700줄, 7개 API·캔버스·힌트 마커·집예측·이어두기)는 유지하고 6곳만 보강: renderHintCard 새 TOP5 마크업(API 가 이미 주던 scoreLead 표시), 안내문 토글 3곳, `syncSideInfo()` 신설(대국자·현재 수·내 돌 동기화), resetToSetup 사이드 초기화.
- 화면: `templates/game/result.html` — **실력 리포트(핵심 화면)**. `app-header.css` + 전용 `static/css/result.css`(common.css 미사용). 구성: breadcrumb → 제목+액션(기보 목록·주석 SGF) → 기보 메타 6칸 → 한 줄 총평 배너(최종 형세 | 총평 | 가장 큰 승부처) → [왼쪽: 복기 바둑판 카드(대국자·승률바·캔버스·AI승률·컨트롤·수 정보·이 수부터 다시 두기) + 승부 흐름 | 오른쪽: 예상 기력·구간별 성적·AI 착수 통계·패착·핵심 실수·호수] → 학습 요약 2칸 → 나란히 비교 모달. **JS 약 1,100줄은 그대로 유지**(캔버스 판·수순·변화도·집예측·놓아보기·나란히 비교·휠 이동·패착/실수/호수/총평/기력 렌더·flowCanvas). JS 가 참조하는 51개 id 와 생성 클래스(.re-*/.losing-*/.mistake-*/.gm-*/.info-*/.grade-badge/.g-*/.active)는 이름 유지 — 스크립트 계약. 삭제한 것은 옛 2카드 사이드 전용 `syncSideHeight()` 뿐. 추가 JS: 총평 배너 좌·우 슬롯(#summaryFinal/#summaryKeyMove)과 학습 요약(#lessonGood/#lessonNext)을 moves/keyMistakes/losingMove/goodMoves 로 생성.
- 화면: `templates/game/waiting.html` — **분석 대기**. `app-header.css` + 전용 `static/css/waiting.css`(common.css 미사용). [왼쪽 분석 진행 카드 | 오른쪽 사활 위젯] 2열. 왼쪽: 파일·총 수·visits / 진행률 바 / 현재 분석 수·정밀도·경과 시간 / 분석 과정 4단계(파싱→1차 수순별→2차 정밀 재분석→리포트 생성; progress<99=1차, ≥99=2차, DONE=전부 완료로 매핑) / 완료·오류 배너. 폴링은 기존 `/game/status/{jobId}` 그대로(RUNNING·DONE·ERROR·UNKNOWN 4분기, DONE 시 자동 이동하지 않고 `/game/result/{id}` 버튼 노출 — 사활 풀이 중 이탈 방지). 남은 시간은 경과×(100-p)/p 로 클라이언트 추정. 사활 위젯 JS(약 240줄, `/api/tsumego/random` 연동·바둑 규칙·정해 판정)는 **그대로 이식**했고 `.ts-*` 클래스명이 JS 계약이라 이름 유지, 스타일만 교체. 컨트롤러 waiting 에 `totalMoves`(getRawMoves 파싱)·`analysisVisits`·`deepVisits` 추가(KataGoService 주입).
- 화면: `templates/game/index.html` — **기보 목록(표형)**. `app-header.css` + 전용 `static/css/game-list.css`(common.css 미사용). 히어로(전체 기보/평균 AI 일치율/누적 분석 시간) → 탭(전체·프로기보·나의기보·분석 완료·미분석) + 전체 재분석 → 검색·기간·종류·정렬 → 표(기보 정보|분석 상태|AI 일치율 링|구간별 일치율 3바|분석 일시+소요|액션) → 클라이언트 페이징(10/20/50). 검색·필터·정렬·페이징은 전부 JS(서버 라운드트립 없음). 기존 폴링 2종(`/game/running-jobs`, `/game/reanalyze-all/status`) 유지. `SingleGameViewController.index` 는 기존 4개(files/proFiles/results/resultMap)에 `resultByFile`·`accuracyByFile`·`avgAccuracy`·`totalAnalysisMinutes` 추가(`overallMatchRate()` = 구간 일치율의 수 가중평균).
- 화면: `templates/home.html` — **상단 헤더형 독립 레이아웃**(로그인과 동일하게 common.css 미사용, 전용 `static/css/home.css`). 사이드바 셸 대신 sticky 헤더(브랜드·가로 내비 7개·계정 `<details>` 드롭다운(로그아웃 POST)·모바일 햄버거). 구성: 히어로(인사·CTA·장식 바둑판 + 최근 분석 요약 링 + 정확도 추이) → 바로가기 6칸 → [최근 분석 결과 4카드 | 나의 실력 현황] → 오늘의 학습 추천. **지표는 전부 기존 `recent` 바인딩에서 계산**: 카드 썸네일=각 기보의 실제 최종 국면(canvas), AI 일치율=`moves.![matchesBest]` 비율, 평균 집손해=`moves.![scoreLoss]`, 예상 기력=result.html 과 같은 rankBand(avgLoss), 추이=기보별 일치율. HomeController 는 `totalGames`(전체 분석 기보 수)만 추가.
- 화면: `templates/login.html` — **앱 셸 밖의 독립 레이아웃**. common.css를 link 하지 않고 전용 `static/css/login.css`만 쓴다(사이드바 그리드·패널 시스템과 완전 분리). 2단 구성: 왼쪽 네이비 히어로(브랜드·기능 4개·원근 바둑판 장식) | 오른쪽 화이트 로그인 카드. 폼은 `POST /login-local`(username/password), 로그인 유지=체크박스 `remember-me=true`(기본 checked, 해제 시 파라미터 미전송→쿠키 없음). 소셜 버튼은 `googleEnabled/naverEnabled` 참일 때만 렌더. 반응형 1050/780/420px, 780px 이하 세로 2단. 각 페이지 topnav 우측에 **사용자 메뉴**(`<details class="user-menu">` 무JS 드롭다운: 아바타+이름+캐럿 → 이름·이메일·로그아웃). 모델 주입: `controller/GlobalUserAdvice`(@ControllerAdvice) `currentUserName/Email/Picture/Initial` — 구글=name/email/picture 속성, 네이버=response 중첩, 데모는 표시명. 소셜 로그인 시 아바타=프로필 사진, 이메일 표기.
- Boot 4/Security 7 패키지: PathRequest=`boot.security.autoconfigure.web.servlet`, CommonOAuth2Provider=`security.config.oauth2.client`.

## "DB" 구조 (파일 기반)
| 데이터 | 위치 | 형식 |
|---|---|---|
| 기보 원본 | `katago.record-dir` C:/KataGo/Baduk_Records | `.gib`/`.sgf` |
| 프로 기보 | 같은 폴더, 파일명에 `신진서 vs` 포함 | 〃 |
| 회원 계정 | `app.users-file` C:/KataGo/users.json | JSON 배열(BCrypt 해시) |
| 분석 결과 | `katago.result-dir` C:/KataGo/GameResults | `{uuid}.json` |
| 결과 목록 | 파일명 기준 최신 1건만 노출 (재분석 누적 제거) | - |
| 타이젬 기보 | `tygem.gibo-dir` | `.gib` |
- 설정 전부 `application.yaml`. 경로/타임아웃/모델 변경은 여기서만.

## 핵심 제약 (위반 잦음)
- Thymeleaf 3.1: 인라인 JS 표현식 제약 → 동적 동작은 정적 `onclick`+JS로.
- 기존 저장 JSON은 구 등급(S/A/B/C/D) 가능 → 새 등급 보려면 재분석 필요.
