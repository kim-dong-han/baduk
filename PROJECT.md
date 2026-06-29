# PROJECT

> Claude 진입점. 새 세션은 이 파일 → 필요 시 ARCHITECTURE.md/RULES.md 순으로만 읽는다.
> 코드 전체 재탐색 금지. 사실(facts)은 여기와 ARCHITECTURE.md에 고정되어 있다.

## 소개
바둑 기보 AI 분석 웹앱. 내 기보를 KataGo와 비교해 초반/중반/종반 구간별 실력을 수치화하고, 프로 기보(`신진서 vs`)와 비교한다.

## 기술 스택
| 영역 | 사용 | 버전 고정 |
|---|---|---|
| 언어 | Java | 21 |
| 프레임워크 | Spring Boot | 4.1.0 |
| 뷰 | Thymeleaf (cache off) | 3.1 제약 |
| JSON | Jackson | - |
| 보일러플레이트 | Lombok | - |
| 크롤링 | Selenium + webdrivermanager | 4.27.0 / 5.9.3 |
| 빌드 | Gradle | wrapper |
| AI 엔진 | KataGo (로컬 subprocess) | v1.16.5 opencl |

## 목표
1. 기보(GIB/SGF) 업로드 → KataGo 분석 → 구간별 지표
2. 일반인이 이해하는 직관적 복기 UI (등급: 최선/좋음/보통/실수/악수)
3. 프로 기보 비교

## 진행 현황
- 상태/할 일은 **TODO.md** 한 곳에서만 관리 (memory 중복 금지)
- 실행: `.\gradlew.bat bootRun` → http://localhost:8081
- GitHub: https://github.com/kim-dong-han/baduk
