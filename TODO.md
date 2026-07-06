# TODO

> 단일 상태 소스(single source of truth). 진행 현황은 여기서만 관리.
> 작업 끝나면 진행중→완료로 이동(요약 1줄). 길어지면 완료 오래된 건 잘라낸다.

## 진행 중
- (없음)

## 방향 (2026-07-03 확정)
- **배포 중단** — 개인 GPU 연산 부담. 이 저장소=포트폴리오, 엔진 로컬, b28 고정. 배포판은 추후 별도 저장소. (PROJECT.md 프로젝트 성격 참조)
- 초점: **세부 페이지 콘텐츠 고도화**.

## 예정 / 후보 (페이지 콘텐츠, 우선순위 ⭐)
- 분석 메타 패널(net·visits·소요시간·수), 샘플 기보 갤러리(정적 데모 결합)
- **면접용 정적 데모**: 결과 뷰어+실력 리포트만 정적 배포(GitHub Pages)
- `.card` box-shadow 통합은 HTML 공용 클래스 필요해 보수적 보류 유지

## 최근 완료 (최신순, 5건 유지)
- **오답노트/북마크(`/notes`)**: AnalysisService.getUserMistakeNotes() — 내 기보 실수·악수 수를 집손해순 수집(상한200). MistakeNote DTO, AnalysisController `/notes`. notes.html: 카드(집손해·등급·색·수번·실제→최선·구간·기보명)+필터(전체/악수만/⭐북마크)+localStorage 북마크(`badukNoteBookmarks`, key=`gameId:turn`). result.html에 `?move=N` 딥링크 지원(init에서 우선). 전 페이지 topnav "오답노트" 링크 추가. 브라우저서 렌더·북마크 persist·딥링크(move=6→6수) 검증 완료
- **실력 리포트 "반복 약점" 자동 코멘트**: AnalysisService.getUserWeaknesses() — 내 기보(non-pro) 전체를 구간·등급·집손해·수번으로 집계해 WeaknessInsight 리스트 생성(재분석 X). 코멘트: ①반복 약점 구간(최저 유사도, 2구간이면 비교/1구간이면 집중복기 안내) ②큰 실수(악수) 빈도+몰리는 구간 ③구간 집손해+구간별 팁 ④강점 격려. batch.html 상단 "📋 코칭 코멘트" 카드(severity high/mid/good 색상). AnalysisController + /api 배선. 서버 재기동 후 브라우저 렌더 검증 완료(현 데이터=초반만 있어 2건 표시). 주의: 구 등급 JSON은 악수 미집계
- **내 수 vs 최선수 나란히 비교**: result.html 모달(`나란히 비교` 버튼, 최선수와 다를 때만 활성). 같은 직전 국면에서 좌=실제 진행(내 수+이후 실착 6수), 우=bestPv를 두 판에 유령돌로 나란히. 각 판 흑승률·예상형세(우측은 candidates[0], 없으면 winrateBefore)·PV텍스트·손해 요약. drawPvSequence 재사용, ESC/배경클릭 닫기. 기존 저장 JSON으로 브라우저 렌더·버튼 disable·닫기 검증 완료
- 후보수(①②③) hover 상세: MoveDetail.candidates(move·winrate·scoreLead·pv 상위3) 추가 → result.html에서 마커 hover 시 그 수의 예상 진행을 유령돌로, 승률·형세를 툴팁으로 표시(drawPvSequence로 변화도 로직 재사용). 재분석 필요. 로컬 재분석으로 hover·원복 검증 완료
- ⭐ About/기술 페이지(`/about`) + README.md: 히어로·기능카드·아키텍처 다이어그램·스택·직접구현 하이라이트·설계결정. 전 페이지 topnav에 "소개" 링크 추가(result.html은 AI 대국 링크도 누락됐어 함께 보강). README는 스크린샷 `<details>`로 배선, 이미지는 `docs/images/` 가이드대로 추가 필요. About 페이지 렌더 검증 완료
- `/api/play/pass` 엔드포인트 추가 — 패스 버튼이 404였던 기존 버그 수정(playUserMove("pass") 위임, 2연속 패스 종료). curl 검증 완료

## 주의/미해결
- 기존 저장 JSON 일부 구 등급(S/A/B/C/D) → 재분석해야 새 등급 반영.
