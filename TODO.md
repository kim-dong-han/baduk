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
- **로그인: 소셜(구글·네이버) 버튼 임시 숨김 → 아이디 로그인만**: 랜덤 cloudflared 터널로 외부 공유 시 구글 OAuth 리디렉션이 콘솔 등록 주소(localhost)와 불일치→"액세스 차단". 도메인 확정 전까지 아이디 로그인만 노출. **마스터 스위치** `app.social-login-enabled`(기본 false) 추가 — AuthController에서 `socialLoginEnabled && googleEnabled/naverEnabled`로 gate. google-oauth.properties·OAuth 설정은 그대로 보존, 나중에 고정 도메인+콘솔 리디렉션 등록 후 yaml에서 true로만 바꾸면 복구. 검증: /login HTML에 oauth2 버튼 없음·아이디폼/회원가입 유지. ※고정 도메인 전환 시엔 `server.forward-headers-strategy: framework`도 필요(프록시 뒤 https 리디렉션).
- **버그수정: 상단 내비 '분석판' 링크 글자 깨짐(mojibake)**: 이전 세션 바이트 삽입 오류로 8개 템플릿(home·about·result·play·index·notes·gallery·batch)에서 `분석판`이 `ë¶ìí`로 저장돼 있던 것 발견(study.html만 정상). 코드포인트로 생성한 정상 UTF-8로 일괄 교정(regex `<a href="/study">[^<]*</a>`). 검증: /about·/game·/play·/gallery 서빙 HTML 분석판-ok·mojibake=False.
- **TRT 가속 활용 3종 기능 추가(참고도·실시간 집·일괄 재분석)**: ①**참고도(PV)+추천수 10개** — KataGoService `Candidate`에 `pv`(참고도 GTP 수순) 필드 추가, `getTopMoves`·`analyzeTop`가 moveInfos[].pv 파싱. `/api/analyze/top` topN 6→10, 응답에 `pv`·`ownership` 포함. study.html: 추천수 목록 클릭→그 뒤 예상 진행을 판에 번호(반투명 돌, 따냄 반영)로 표시(`showPv`/`clearPv`/`drawPv`/`drawGhostStone`), 메달 10개. ②**실시간 집(영역) 히트맵** — `analyzeTop` 쿼리에 `includeOwnership=true`, `TopResult.ownership`(361·흑기준) 추가. study.html '🏳️ 집 예측 표시' 토글(`drawOwnership`, result.html과 동일 매핑 `(18-gy)*19+gx`). ③**전체 기보 일괄 재분석** — SingleGameService `startBulkReanalyze`(@Async, 일반+프로 전체 순차 analyze, `BulkStatus` 진행상태) + SingleGameViewController `POST /game/reanalyze-all`·`GET /game/reanalyze-all/status`. game/index.html에 '🔁 전체 재분석' 카드+진행바 폴링(완료 시 자동 새로고침). 검증(임시로그인 curl): `/api/analyze/top` ok=True·ownership 361·후보 10·cand0 pv 10수 확인. users.json 백업·복원(원래 부재→제거). Java/템플릿 변경→재빌드·재시작 완료. ※AI대국(play.html)·놓아보기(result.html)엔 아직 참고도/실시간집 미적용(원하면 확장).
- **visits 상향(TRT 여유 활용)**: 배치 기보분석 `analysis-visits` 200→**1000**(application.yaml, 정확도↑, TRT라 158수 전수 ~30초 수준 추정), 실시간 분석 하드코딩 100→**500**(KataGoService 3곳: getBestMoveEval·getTopMoves·analyzeTop = AI대국 힌트·실시간 분석판/study·놓아보기). 형세판단(300)은 유지. deep-visits 1500은 그대로(analysis-visits 1000<1500이라 2차 정밀분석 계속 동작). Java/yaml 변경→재빌드·재시작 완료.
- **엔진 교체: OpenCL → TensorRT(GPU) — 실시간 분석 ~9배 가속**: 사용자 데스크톱 통합팩(`C:\baduk_ai`)이 훨씬 빠른 이유를 비교분석→**백엔드 차이**가 주원인. 우리 `katago.path`를 통합팩의 **`C:/baduk_ai/lizzie/katago_trt.exe`(KataGo v1.17.1 TensorRT)** 로 교체(모델 b28·config 동일 유지→결과 불변·속도만↑). GPU=RTX 4060 확인. KataGoService: 3곳 ProcessBuilder에 `pb.directory(exe폴더)` 추가(백엔드 DLL nvinfer/cudnn/cublas + `KataGoData/trtcache` 해석용). TRT 첫 실행=타이밍캐시 빌드(~3분, 1회, 캐시 영속)→미리 CLI로 구워둠. 서버 시작당 TRT 엔진 초기화(~20~27초)로 첫 요청이 느린 문제→신규 `EngineWarmup`(ApplicationReadyEvent서 백그라운드로 빈 판 getTopMoves 재시도 예열). 실측(analyze/top 100v): 예열 후 첫 요청 0.57s→이후 **0.06~0.1s**(OpenCL 워밍 ~0.63s 대비 ~9배). 관점·후보 정상. ※되돌리려면 yaml path를 opencl exe로. Java/yaml 변경→재시작 완료
## 주의/미해결
- 기존 저장 JSON 일부 구 등급(S/A/B/C/D) → 재분석해야 새 등급 반영.
