# 바둑 기보 AI 분석기 (Baduk Analyzer)

업로드한 바둑 기보를 **KataGo**(AlphaGo 계열 오픈소스 엔진)로 분석해 초반·중반·종반 구간별 실력을 수치화하고, 프로 기보와 비교하는 **Spring Boot 웹앱**입니다. AI 최선수·집(영역) 예측·실수 지점을 직관적으로 복기하고, 그 국면에서 AI와 직접 이어 둘 수 있습니다.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)
![KataGo](https://img.shields.io/badge/KataGo-b28c512nbt-1a1a2e)
![License](https://img.shields.io/badge/License-personal-lightgrey)

> **개인 포트폴리오 프로젝트** — KataGo는 GPU 연산 부담이 커서 상용 배포 대신 엔진을 **로컬**에서 구동합니다. 앱 내 `/about` 페이지에 기술 소개가 정리되어 있습니다.

> **정적 데모** — 연산이 필요 없는 부분(복기 뷰어·실력 리포트·샘플 갤러리)을 미리 렌더해 [`docs/`](docs/)에 정적 스냅샷으로 담았습니다. 저장소 설정에서 GitHub Pages 소스를 `main`/`docs`로 지정하면 공개됩니다. 재생성: 로컬 서버 실행 후 `bash scripts/build-docs.sh`.

---

## 스크린샷

<details>
<summary>화면 보기 (이미지 추가 후 펼쳐짐 · 촬영 가이드는 <a href="docs/images/README.md">docs/images</a>)</summary>

| 복기 + AI 집 예측 히트맵 | 실력 리포트 |
|---|---|
| ![복기 히트맵](docs/images/review-heatmap.png) | ![실력 리포트](docs/images/skill-report.png) |

| AI 대국 · 이어두기 | 기술 소개 |
|---|---|
| ![AI 대국](docs/images/play.png) | ![소개](docs/images/about.png) |

</details>

---

## 주요 기능

- **실력 리포트** — 여러 기보를 집계해 구간별 AI 유사도·집 손해·수 품질 분포를 보여주고 프로 기보와 비교
- **한 판 복기** — 수별 최선수·집 손해·등급(최선~악수), 형세·승률 변화 그래프
- **AI 집(영역) 예측 히트맵** — KataGo의 `ownership` 예측을 바둑판에 확신도 비례로 오버레이
- **이 수부터 AI와 다시 두기** — 복기 중 특정 국면을 그대로 대국으로 이어받아 다른 수를 시도
- **AI 실시간 대국** — KataGo와 직접 대국 (영구 프로세스로 착수 지연 최소화)
- **사활 문제** — 분석 대기 중 랜덤 사활 풀이 (KataGo로 정답 검증한 60문제, 난이도 3단계)
- **타이젬 자동 연동** — 대국 종료 기보를 감지해 자동 분석

## 시스템 구조

```
브라우저 (Thymeleaf · Canvas 2D)
   │  HTTP
Controller ── Service ── Parser/Util (GIB·SGF, 좌표 변환)
                 │
            KataGoService ──(ProcessBuilder, JSON stdin/stdout)── katago.exe
                 │
            분석 결과 JSON 파일 (C:/KataGo/GameResults/{uuid}.json)
```

- DB 없이 **로컬 파일 시스템**이 영속 계층 (분석 결과 = `{uuid}.json`)
- KataGo는 **analysis 엔진 모드**로 통신 — 기보를 JSON 쿼리로 보내고 수별 승률·집·최선수·집 예측을 파싱

## 기술 스택

| 영역 | 사용 |
|---|---|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.1 (MVC, `@Async`) |
| 뷰 | Thymeleaf + 바닐라 JS, HTML Canvas 2D |
| AI 엔진 | KataGo v1.16.5 · `kata1-b28c512nbt` (로컬) |
| 차트 | Chart.js |
| 크롤링 | Selenium + webdrivermanager |
| 빌드 | Gradle Wrapper |

## 실행 방법

**필요 조건**
- Java 21
- 로컬에 KataGo 설치 + 신경망 모델 (경로는 `src/main/resources/application.yaml`의 `katago.*`에서 설정)

```bash
./gradlew.bat bootRun      # Windows
# → http://localhost:8081
```

주요 경로: `/analysis/batch`(실력 리포트) · `/game`(복기) · `/play`(대국) · `/about`(소개)

## 직접 구현한 부분

- **KataGo Analysis Engine 연동** — `ProcessBuilder`로 엔진과 JSON 통신, 기보 전체를 쿼리로 보내 수별 지표 파싱. 실시간 대국은 영구 프로세스 재사용
- **GIB / SGF 파서 & 좌표 변환** — 타이젬 `.gib`·`.sgf` 직접 파싱, SGF↔GTP 좌표 변환, 보드 크기·대국자·핸디캡 처리
- **하이브리드 분석 쿼리** — 형세는 매 수 `visits=1`, 실력 지표는 10수마다 `visits=30`으로 정확도·속도 절충
- **집(영역) 예측 히트맵** — `includeOwnership`로 받은 361칸 소유 예측을 `reportAnalysisWinratesAs=BLACK` 관점에 맞춰 정규화·시각화
- **복기 ↔ 대국 연결** — 차례 판정을 "마지막 수의 반대 색"으로 계산해 백선착·접바둑 국면까지 정확히 처리
- **사활 문제 생성 파이프라인** — 공개 SGF를 로컬 KataGo로 정답 검증 후 정해 길이로 난이도 3등분

## 설계 결정 & 한계

- **로컬 엔진 전용** — 상용 배포 대신 강한 신경망(b28)을 로컬에서 고정 사용. 연산이 필요 없는 복기 결과·실력 리포트는 정적 데모로 분리 가능
- **파일 기반 저장** — RDB 대신 JSON 파일로 구조 단순화
- 상용 "배포판"은 추후 별도 저장소로 분리 예정

---

*개인 프로젝트 · KataGo 기반 바둑 분석 웹앱*
