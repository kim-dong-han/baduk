# ARCHITECTURE

> 이 파일이 코드 탐색을 대체한다. 위치를 모를 때 Grep 하기 전에 여기를 먼저 본다.
> 구조가 바뀌면 코드와 함께 이 파일도 수정한다 (PR/커밋에 포함).

## 시스템 구조
```
브라우저(Thymeleaf)
   │  HTTP
Controller ── Service ── Parser/Util
                 │
            KataGoService ──(ProcessBuilder/stdin·stdout JSON)── katago.exe
                 │
            JSON 결과 파일 (C:/KataGo/GameResults/*.json)
```
- KataGo는 DB가 아니라 **로컬 subprocess + 파일 시스템**이 영속 계층이다.
- 분석 결과는 RDB가 아닌 `result-dir`의 `{uuid}.json`으로 저장/조회.

## 패키지 구조 (`com.example.badukanalyzer`)
```
controller/   요청 매핑만. 로직 없음.
  SingleGameViewController  /game/**        (화면, 비동기 분석)
  SingleGameController      /api/game/**    (REST)
  AnalysisController        /analysis/batch (배치 화면)
  UploadController          /upload/gib     (업로드)
  TygemController           /tygem/**       (타이젬 연동)
  TsumegoController         /api/tsumego/** (대기 중 사활 문제)
service/      비즈니스 로직.
  SingleGameService    단일 기보 분석 파이프라인 (등급/구간/저장)
  KataGoService        subprocess 통신, 쿼리 생성, 진행률 콜백
  AnalysisService      배치 분석
  AnalysisJobStore     ConcurrentHashMap 기반 비동기 Job/진행률 (Job.fileName, getRunningJobs)
  TsumegoService       사활 문제 로드(@PostConstruct)·랜덤 제공, 인메모리 List
  GibService/SgfService, TygemCrawlerService, TygemFileWatcherService
parser/       GibParser, SgfParser → List<Move> / TsumegoSgfParser → TsumegoProblem
converter/    SgfConverter
util/         CoordinateConverter (SGF 좌표 ↔ GTP 좌표)
domain/       Move, Game, AnalysisResult
dto/          MoveDetail(bestPv 포함), SingleGameResult, AnalysisResponse, UploadResponse, TsumegoProblem
```
화면 템플릿: `resources/templates/game/{index,result,waiting}.html`, `analysis/batch.html`
공통 CSS: `resources/static/css/common.css` (topnav/.page/reset/media query)
사활 문제: `resources/tsumego/*.sgf` (번들. 파일 추가 시 자동 인식, 재시작 필요)

## 사활(Tsumego) 위젯 — 대기 중 학습
- 목적: 분석 대기(waiting.html) 동안 랜덤 사활 문제를 풀게 해 체감 대기시간↓.
- 로드: `TsumegoService.load()` `@PostConstruct`로 `classpath*:tsumego/*.sgf` 1회 파싱 → 인메모리.
- API: `GET /api/tsumego/random?difficulty=&exclude=`(없으면 204), `GET /api/tsumego/count`.
  - `difficulty`(쉬움/보통/어려움/전체·생략=전체) 필터, `exclude`=직전 문제 id 제외 → "다음 문제"가 항상 바뀜. 후보 없으면 단계적 완화(문제 수 제한 없음).
- `TsumegoProblem`: stones(초기배치)/answers(정답 첫수,복수)/solution(정해)/region(코너 확대)/difficulty.
- 난이도 판정(TsumegoSgfParser.detectDifficulty): 파일명 접두사(쉬움_/보통_/어려움_ 또는 easy/normal/hard) → SGF `DIFF[]` → 정해 수순 길이(≥8 어려움/≥4 보통/그외 쉬움).
- 좌표는 전부 GTP. 정답 미추출 문제는 로드 시 건너뜀.

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
- 없음 (로컬 단일 사용자 데스크톱 앱). 인증 도입 시 이 절을 갱신.

## "DB" 구조 (파일 기반)
| 데이터 | 위치 | 형식 |
|---|---|---|
| 기보 원본 | `katago.record-dir` C:/KataGo/Baduk_Records | `.gib`/`.sgf` |
| 프로 기보 | 같은 폴더, 파일명에 `신진서 vs` 포함 | 〃 |
| 분석 결과 | `katago.result-dir` C:/KataGo/GameResults | `{uuid}.json` |
| 결과 목록 | 파일명 기준 최신 1건만 노출 (재분석 누적 제거) | - |
| 타이젬 기보 | `tygem.gibo-dir` | `.gib` |
- 설정 전부 `application.yaml`. 경로/타임아웃/모델 변경은 여기서만.

## 핵심 제약 (위반 잦음)
- Thymeleaf 3.1: 인라인 JS 표현식 제약 → 동적 동작은 정적 `onclick`+JS로.
- 기존 저장 JSON은 구 등급(S/A/B/C/D) 가능 → 새 등급 보려면 재분석 필요.
