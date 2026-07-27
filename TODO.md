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
- **AI 대국 형세 판단 + 계가**: /play. KataGoService.evaluatePosition(includeOwnership, maxVisits 300 → rootInfo.scoreLead·winrate·ownership361) + PlayService.estimate() + `POST /api/play/estimate`. play.html: 형세판단(btnEstimate)=현재 집차·흑승률 카드+집영역 오버레이(drawOwnershipPlay, +흑/−백 사각형, 돌 아래), 계가하기(btnScore)=같은 데이터 '흑/백 N집 승' 문구, 더블패스 종료 시 requestFinalScore 자동 계가(gameOverSub에 표시). estOwnership는 착수/패스/무르기/리셋 시 clearEstimate. 검증: 형세 "백 +0.3집·흑47%"+오버레이, 계가 "백 0.3집 승". ※Java 변경 → 재시작 필요
- **벤치마킹 4종(KaTrain·Lizzie·GRP) 기능 추가**: ①예상 기력(result.html, renderRankEstimate) — moves의 색별 평균 scoreLoss→rankBand로 흑/백 기력대 추정 헤드라인 배너(재미용 근사, 클라이언트). ②추천 히트맵(btnHeat) — 후보수를 ①②③ 대신 승률 색(winColorHsl, 초록=좋음)+% 원반으로, 둘 쪽 관점. ③주석 SGF 내보내기 — `GET /game/result/{id}/sgf`(SingleGameViewController)+SingleGameService.buildAnnotatedSgf(수번·등급·집손해·최선수 C[] 코멘트, CoordinateConverter.fromGtp→toSgfX/Y), 결과판 다운로드 링크. ④실시간 수 평가(KaTrain) — /play 착수마다 '최선 대비 -N집' 카드(moveEvalCard). KataGoService.getBestMoveEval(move+rootInfo.scoreLead 반환), PlayService.playUserMove가 착수前 최선lead와 착수後 lead 차로 손해 계산(+1 KataGo 호출/수, isBlack부호), getLastUserLoss→/api/play/move 응답 userLoss. 브라우저 검증: 기력배너·히트맵(57%초록/4%빨강)·SGF(4536B)·실시간(Q16최선/1선악수 -7.9집). ※Java 변경 → bootRun 재시작 필요

- **놓아보기(검토중 애니) + AI대국 최선수 힌트**: ①결과판 놓아보기(result.html, btnTry) — 현재 국면 위 캔버스 클릭으로 흑·백 교대 착점(기존 getGroup/hasLiberty로 따냄·자살수 금지), 툴바(되돌리기/처음으로/나가기)·카운터·Esc종료, hover/화살표 비활성. **검토중 배지**(tryToolbar): CSS revGlow/revPulse(보라 펄스링+점)+JS 말줄임(검토중→···, 450ms setInterval, start/stopReviewingAnim). ②**AI대국(/play) 최선수 힌트**: 사용자 요청으로 결과판 힌트 제거→대국판으로 이동. btnHint(내 차례만)→`POST /api/play/hint`(PlayController)→PlayService.getHint()=kataGoService.getBestMove(history), 판 안 바꿈. 응답 좌표를 **텍스트 없이** 바둑판에 금색 펄스 마커(drawHintMarker, requestAnimationFrame로 redraw 반복, sin 맥동)로만 표시. 착수/패스/무르기/리셋 시 clearHint. 브라우저 검증 완료(놓아보기 검토중 애니·/play 힌트 R16 마커). ※Java 변경 → bootRun 재시작 필요(포트 kill 후 재기동, processResources가 템플릿도 복사)
- **패착(승부 분기점) 감지**: result.html 클라이언트에서 저장 winrate로 계산(재분석X, 기존 전 기보 즉시 적용). 규칙=**후보는 집손해≥1.5집(보통 이상)**, 둔 쪽 승률 ≥40%(팽팽/유리)였다가 이 수로 <50%(열세)로 넘어간 수, **이후 그 쪽이 회복(다시 50%↑) 안 한 것(stuck) 우선**, 없으면 하락폭 최대. UI: side 최상단 "🔑 패착" 카드+승률차트 금색선+핵심실수 목록 패착/승부수 배지(패착 뒤 같은 색 실수·악수)+moveInfo 주석. 프로 완패/점진패는 '뚜렷한 패착 없음'(정상). **사용자 피드백으로 2건 수정**: ①패착이 '집은 조금 잃고 승률만 확 무너지는 보통 등급'일 수 있어(예: 이원영전 101수 E6 집1.9·승49→22) grade 필터→집손해≥1.5로 확대 ②recovered 판정이 매 수 관점 뒤집히던 버그를 진 쪽 기준으로 고정. 1000v 재분석으로 101수(E6) 정확히 잡힘 검증. 임계값 40/50/1.5는 조정 여지
- **listResults 성능 개선(요약 파서+캐시)**: 목록·집계용 `listResultSummaries()` 신설 — 수당 무거운 필드(ownership·candidates·bestPv·topMoves, 상세 페이지에서만 사용)를 MoveDetailSummaryMixin으로 스킵하고, result-dir 서명(개수·이름·mtime) 동일하면 캐시 재사용(saveResult에서 무효화). AnalysisService(집계·약점·오답·갤러리·count)·getResultMap·인덱스뷰가 이걸 사용. 상세는 기존 getResult(full)·API `/api/game/results`는 full 유지(계약 불변). 검증: 갤러리19·리포트 matchRate·오답노트 동일, 상세 179수 candidates/bestPv/ownership 유지, 반복요청 ~4ms
- **GitHub Pages 활성화**: 2026-07-27 `docs/` 정적 데모 공개(gh api, main/docs) — https://kim-dong-han.github.io/baduk/ 라이브(랜딩·report·result-*.html). 발표 백업용

## 주의/미해결
- 기존 저장 JSON 일부 구 등급(S/A/B/C/D) → 재분석해야 새 등급 반영.
