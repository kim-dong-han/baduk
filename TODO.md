# TODO

> 단일 상태 소스(single source of truth). 진행 현황은 여기서만 관리.
> 작업 끝나면 진행중→완료로 이동(요약 1줄). 길어지면 완료 오래된 건 잘라낸다.

## 진행 중
- (없음)

## 방향 (2026-07-03 확정)
- **배포 중단** — 개인 GPU 연산 부담. 이 저장소=포트폴리오, 엔진 로컬, b28 고정. 배포판은 추후 별도 저장소. (PROJECT.md 프로젝트 성격 참조)
- 초점: **세부 페이지 콘텐츠 고도화**.

## 예정 / 후보 (페이지 콘텐츠, 우선순위 ⭐)
- **README 스크린샷 4장 추가** — 툴 제약으로 브라우저 캡처를 저장소로 못 넣음. `docs/images/README.md` 가이드대로 review-heatmap/skill-report/play/about.png 촬영해 넣으면 README `<details>`가 렌더됨. (헤드라인용 히트맵은 pro 기보 `c9ba1000` 종반이 선명)
- 내 수 vs 최선수 변화도 나란히 비교
- 실력 리포트 "반복 약점" 자동 코멘트, 오답노트/북마크
- 분석 메타 패널(net·visits·소요시간·수), 샘플 기보 갤러리(정적 데모 결합)
- **면접용 정적 데모**: 결과 뷰어+실력 리포트만 정적 배포(GitHub Pages)
- `.card` box-shadow 통합은 HTML 공용 클래스 필요해 보수적 보류 유지

## 최근 완료 (최신순, 5건 유지)
- 후보수(①②③) hover 상세: MoveDetail.candidates(move·winrate·scoreLead·pv 상위3) 추가 → result.html에서 마커 hover 시 그 수의 예상 진행을 유령돌로, 승률·형세를 툴팁으로 표시(drawPvSequence로 변화도 로직 재사용). 재분석 필요. 로컬 재분석으로 hover·원복 검증 완료
- ⭐ About/기술 페이지(`/about`) + README.md: 히어로·기능카드·아키텍처 다이어그램·스택·직접구현 하이라이트·설계결정. 전 페이지 topnav에 "소개" 링크 추가(result.html은 AI 대국 링크도 누락됐어 함께 보강). README는 스크린샷 `<details>`로 배선, 이미지는 `docs/images/` 가이드대로 추가 필요. About 페이지 렌더 검증 완료
- `/api/play/pass` 엔드포인트 추가 — 패스 버튼이 404였던 기존 버그 수정(playUserMove("pass") 위임, 2연속 패스 종료). curl 검증 완료
- ⭐ "이 수부터 AI와 다시 두기": result 버튼→선택 수 직전 국면을 sessionStorage로 `/play` 이관(`POST /api/play/from`가 PlayService.history 시드). **부수 수정**: 차례 판정을 수순 파리티→마지막 수 반대색(`sideToMove`/`currentColor`)으로 변경해 백선착·접바둑·이어두기 국면 대응(일반 흑선착 회귀 없음). 백선착 실제 기보로 전체 흐름(시드→백 착수→AI 응수) 검증 완료. 참고: `/api/play/pass` 엔드포인트 부재(패스 버튼 미동작)는 기존 이슈로 잔존
- ⭐ AI 집(영역) 히트맵: 쿼리에 `includeOwnership:true` → MoveDetail.ownership(361, 착점후 국면) 저장 → result.html "집예측" 토글로 반투명 오버레이(+흑/−백, reportAnalysisWinratesAs=BLACK). 재분석 필요. 로컬 재분석 1판으로 방향·부호 검증 완료. 주의: ownership로 결과 JSON 커짐(200수 ~0.6-1MB) → listResults 인덱스 로드 다소 무거워질 수 있음(추후 lazy-load 최적화 후보)
- 페이지별 `<style>` 중복 추가 정리: byte-identical 3건만 common.css로 이동 — `table{border-collapse;width}`(batch/result), `.gauge-label`(batch/result), `@keyframes spin`(batch/play). waiting.html은 common.css 미연결이라 제외. 서버 재기동 후 batch/index/result/play 4페이지 하드리프레시로 검증, 회귀 없음
- CSS 중복정리(common.css, h1/subtitle/badge) 시각 검증 완료: 서버 기동 후 batch/index/result 3페이지 스크린샷 확인, 회귀 없음
- AI 대국 페이지(`/play`) 신규 추가 + KataGo persistent process로 착수 속도 개선
- 실력 리포트 동작: AnalysisService를 기존 복기결과(GameResults/*.json) 집계로 재작성(재분석 X). 내기보 vs 프로(신진서 vs) 구간별. 타이젬 워처는 새 기보 단일분석 트리거
- 형세 정확도: 단일분석 visits 설정화(katago.analysis-visits, 기본 200 유지). 원인=전투국면 visits 부족(5000서 방송값 수렴), 파싱/좌표/부호는 정상
- 사활 바둑 기본규칙(따냄/착수금지) + waiting 무한로딩 수정(UNKNOWN 처리·즉시폴링·pageshow)
- 사활 문제 60개(쉬움/보통/어려움 각 20) 추가: d180cf SGF→KataGo 정답검증→난이도 3등분. 파서 보드크기(SZ) 지원
- 사활 위젯 "다음 문제" 버그 수정 + 난이도 선택(쉬움/보통/어려움) (a92f4a1)
- 사활(Tsumego) 위젯: 대기 중 랜덤 문제 풀이, `/api/tsumego/*` + `resources/tsumego/*.sgf` (원격 병합)
- 파일 목록 인라인 진행바 + 최선수 변화도(bestPv) (836fd5c)
- 분석 대기 페이지 실시간 진행률 % 표시 (ed1da4b)
- 한글 파일명 redirect UnmappableCharacterException 수정 (e836128)
- 원격/로컬 히스토리 분기 → rebase로 정리, 동기화 완료

## 주의/미해결
- 기존 저장 JSON 일부 구 등급(S/A/B/C/D) → 재분석해야 새 등급 반영.
