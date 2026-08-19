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
공통 CSS: `resources/static/css/common.css` — **다크 좌측 사이드바 셸**. 마크업은 여전히 각 템플릿의 `nav.topnav`(중복, 프래그먼트 없음)이지만 CSS만으로 세로 사이드바로 전환: `position:fixed` 234px 다크 레일, 링크 아이콘=href 기반 `::before`(예 `a[href="/game"]::before`), 사용자 카드=사이드바 하단(dropdown 위로 열림). 본문 밀기=`body:has(nav.topnav){padding-left:234px}`(로그인/회원가입/대기 화면은 topnav 없어 제외). ≤900px에선 상단 가로바로 복귀. **`nav.topnav.rail`**(아이콘 레일 모드, 현재 study.html만 사용): 62px로 접고 hover/focus-within 시 234px로 펼침, 본문 여백은 `--rail-w` 고정이라 펼쳐도 리플로우 없이 위에 겹침. 글자는 `font-size:0`으로 숨기고 `::before` 아이콘(고정 px)만 남기는 방식. play/study의 인라인 `body{padding:0}`는 `:has` 명시도가 이겨 사이드바와 안 겹침.
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
- 화면: `templates/login.html`(히어로+카드, 소셜=구글·네이버 2종, 그라데이션 배경/버튼). 각 페이지 topnav 우측에 **사용자 메뉴**(`<details class="user-menu">` 무JS 드롭다운: 아바타+이름+캐럿 → 이름·이메일·로그아웃). 모델 주입: `controller/GlobalUserAdvice`(@ControllerAdvice) `currentUserName/Email/Picture/Initial` — 구글=name/email/picture 속성, 네이버=response 중첩, 데모는 표시명. 소셜 로그인 시 아바타=프로필 사진, 이메일 표기.
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
