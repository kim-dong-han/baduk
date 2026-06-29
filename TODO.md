# TODO

> 단일 상태 소스(single source of truth). 진행 현황은 여기서만 관리.
> 작업 끝나면 진행중→완료로 이동(요약 1줄). 길어지면 완료 오래된 건 잘라낸다.

## 진행 중
- (없음)

## 예정 / 후보
- CSS 중복정리 시각 검증 (서버 띄워 batch/index/result 렌더 확인) — 미수행
- 페이지별 `<style>` 중복(card box-shadow 등) 추가 정리 — 보수적 보류

## 최근 완료 (최신순, 5건 유지)
- 사활 문제 60개(쉬움/보통/어려움 각 20) 추가: d180cf SGF→KataGo 정답검증→난이도 3등분. 파서 보드크기(SZ) 지원
- 사활 위젯 "다음 문제" 버그 수정 + 난이도 선택(쉬움/보통/어려움) (a92f4a1)
- 사활(Tsumego) 위젯: 대기 중 랜덤 문제 풀이, `/api/tsumego/*` + `resources/tsumego/*.sgf` (원격 병합)
- 파일 목록 인라인 진행바 + 최선수 변화도(bestPv) (836fd5c)
- 분석 대기 페이지 실시간 진행률 % 표시 (ed1da4b)
- 한글 파일명 redirect UnmappableCharacterException 수정 (e836128)
- 원격/로컬 히스토리 분기 → rebase로 정리, 동기화 완료

## 주의/미해결
- 기존 저장 JSON 일부 구 등급(S/A/B/C/D) → 재분석해야 새 등급 반영.
