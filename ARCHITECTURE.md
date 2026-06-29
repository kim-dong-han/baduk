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
service/      비즈니스 로직.
  SingleGameService    단일 기보 분석 파이프라인 (등급/구간/저장)
  KataGoService        subprocess 통신, 쿼리 생성, 진행률 콜백
  AnalysisService      배치 분석
  AnalysisJobStore     ConcurrentHashMap 기반 비동기 Job/진행률
  GibService/SgfService, TygemCrawlerService, TygemFileWatcherService
parser/       GibParser, SgfParser  → List<Move>
converter/    SgfConverter
util/         CoordinateConverter (SGF 좌표 ↔ GTP 좌표)
domain/       Move, Game, AnalysisResult
dto/          MoveDetail, SingleGameResult, AnalysisResponse, UploadResponse
```
화면 템플릿: `resources/templates/game/{index,result,waiting}.html`, `analysis/batch.html`
공통 CSS: `resources/static/css/common.css` (topnav/.page/reset/media query)

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
