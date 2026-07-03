# TODO

> 단일 상태 소스(single source of truth). 진행 현황은 여기서만 관리.
> 작업 끝나면 진행중→완료로 이동(요약 1줄). 길어지면 완료 오래된 건 잘라낸다.

## 진행 중
- (없음)

## 예정 / 후보
- `.card` 계열 box-shadow 패턴(batch/index/result 다수) — selector마다 padding·border-top 등이 달라 진짜 중복 아님. 통합하려면 HTML에 공용 클래스 추가 필요해 보수적 보류 유지.

## 최근 완료 (최신순, 5건 유지)
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
