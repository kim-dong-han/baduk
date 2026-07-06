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
- **listResults 성능 개선(요약 파서+캐시)**: 목록·집계용 `listResultSummaries()` 신설 — 수당 무거운 필드(ownership·candidates·bestPv·topMoves, 상세 페이지에서만 사용)를 MoveDetailSummaryMixin으로 스킵하고, result-dir 서명(개수·이름·mtime) 동일하면 캐시 재사용(saveResult에서 무효화). AnalysisService(집계·약점·오답·갤러리·count)·getResultMap·인덱스뷰가 이걸 사용. 상세는 기존 getResult(full)·API `/api/game/results`는 full 유지(계약 불변). 검증: 갤러리19·리포트 matchRate·오답노트 동일, 상세 179수 candidates/bestPv/ownership 유지, 반복요청 ~4ms
- **면접용 정적 데모(`docs/`)**: 실행 중 서버의 렌더 결과를 스냅샷→GitHub Pages용 정적 파일. 손수 만든 index.html(랜딩+큐레이션 갤러리 3판: 프로 6214e435/c9ba1000+내기보 2525a1e9)+report.html(실력리포트 스냅샷)+result-*.html 3장+css/js 복사+.nojekyll. sed로 절대경로→상대·백엔드링크는 데모내로·재대국버튼 hidden. scripts/build-docs.sh로 재생성, README 안내. node 정적서버(8090)로 브라우저 검증: 랜딩·복기(179수·판·차트·replay숨김)·리포트(코칭·radar) 정상. **Pages 활성화(공개 배포)는 사용자 몫 — 미실행**
- **샘플 기보 갤러리(`/gallery`)**: AnalysisService.getGalleryItems() — 저장 결과를 카드 요약(제목=대국자 or 파일명, 프로/내기보, 날짜·수, 전체 AI유사도=구간 수가중평균, 구간 집손해 pill, best/worst구간)으로. 프로 먼저 정렬. GalleryItem DTO, AnalysisController `/gallery`, gallery.html(반응형 grid+필터 전체/프로/내기보). 전 페이지 topnav "갤러리" 링크. 브라우저 렌더+필터(전체19/프로18/내1) 검증 완료
- **분석 메타 패널**: result.html 헤더에 🧠엔진net·🔎visits/수·⏱소요시간 추가. SingleGameResult에 engineNet/analysisVisits/analysisDurationMs 필드, SingleGameService.analyze()가 분석시간 측정+KataGoService 값(getNetName 짧은블록 b28c512nbt/getAnalysisVisits) 저장. 구 JSON은 null→th:if로 숨김. 신규 분석부터 표기. 검증: 메타주입 임시JSON으로 렌더 확인(삭제)+구 JSON 무필드 정상 렌더
- **오답노트/북마크(`/notes`)**: AnalysisService.getUserMistakeNotes() — 내 기보 실수·악수 수를 집손해순 수집(상한200). MistakeNote DTO, AnalysisController `/notes`. notes.html: 카드(집손해·등급·색·수번·실제→최선·구간·기보명)+필터(전체/악수만/⭐북마크)+localStorage 북마크(`badukNoteBookmarks`, key=`gameId:turn`). result.html에 `?move=N` 딥링크 지원(init에서 우선). 전 페이지 topnav "오답노트" 링크 추가. 브라우저서 렌더·북마크 persist·딥링크(move=6→6수) 검증 완료

## 주의/미해결
- 기존 저장 JSON 일부 구 등급(S/A/B/C/D) → 재분석해야 새 등급 반영.
