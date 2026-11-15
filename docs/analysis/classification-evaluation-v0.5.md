# 일반 직군 규칙 v0.5와 고정 정답셋 비교

**2026-11-15 · 오프라인 평가 · 제품/DB 미적용.** 사용자가 앞서 AI가 검토한 440개 분류안을 정답으로 지정했다. `gold-v1`의 라벨을 고정하고, 공고 제목·저장 본문에 [일반 판정 규칙](../classification-rules.md)을 적용해 비교했다. ID/URL/공고 전체 제목별 정답 룩업은 사용하지 않았다.

## 평가 범위

- 정답: [gold-v1 manifest](classification-gold-v1.json)이 가리키는 JSON의 `proposedCategories`. 440개, 직군 21개, 정답 태그 554개. 설명과 보류 후보는 라벨로 취급하지 않음.
- 입력: 각 정답 항목에 기록된 본문 SHA-256과 일치하는 최신 저장 본문 스냅샷 440개. 제목·회사·사이트도 일치하는지 검증.
- 범위: 모든 440개를 포함. 불리한 공고/사이트나 미분류 정답 1개를 제외하지 않음.
- 제품에 적용한 결과가 아니며 외부 페이지 요청, 재수집, 캡처, DB 카테고리 변경 없음.
- 같은 440개를 보며 규칙을 보완했으므로 **개발/회귀 평가**다. 새 공고/새 사이트의 독립 평가나 일반화 정확도 측정 결과가 아님.

## 전체 결과

| 지표 | 결과 |
| --- | ---: |
| 공고별 완전 일치 | 378/440 · 85.91% |
| 불일치 공고 | 62 |
| 올바르게 연결한 태그(TP) | 530 |
| 정답에 없는데 추가한 태그(FP) | 47 |
| 정답에 있는데 놓친 태그(FN) | 24 |
| 태그 micro 정밀도 | 91.85% |
| 태그 micro 재현율 | 95.67% |
| 태그 micro F1 | 93.72% |
| 직군별 macro F1 | 96.10% |
| 규칙이 미분류로 남긴 공고 | 8 |
| 정답에 분야가 있지만 규칙은 미분류로 남긴 공고 | 7 |

공고 완전 일치는 **예측한 직군 집합 전체가 정답 집합과 같은 경우**다. 태그 하나가 과하거나 부족해도 실패다. FP/FN은 태그 단위라 같은 공고에서 함께 발생할 수 있고, 그 합을 불일치 공고 수로 해석하면 안 된다. 정답의 미분류 1개(657)는 규칙도 미분류로 유지했다. 미분류 정답이 1개뿐이므로 미분류 판별 성능을 일반화할 수 없다.

## 직군별 결과

| 직군 | 정답 수 | TP | FP | FN | 정밀도 | 재현율 | F1 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 웹 백엔드 | 136 | 122 | 7 | 14 | 94.57% | 89.71% | 92.08% |
| 프론트엔드 | 36 | 36 | 3 | 0 | 92.31% | 100.00% | 96.00% |
| Android | 13 | 13 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| iOS | 6 | 6 | 1 | 0 | 85.71% | 100.00% | 92.31% |
| 풀스택 | 9 | 9 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| 데이터 엔지니어 | 46 | 44 | 7 | 2 | 86.27% | 95.65% | 90.72% |
| ML 엔지니어 | 37 | 35 | 2 | 2 | 94.59% | 94.59% | 94.59% |
| DevOps/SRE | 44 | 44 | 9 | 0 | 83.02% | 100.00% | 90.72% |
| 기획·전략 | 18 | 18 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| PM/PO | 33 | 33 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| 디자인 | 35 | 35 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| 마케팅 | 14 | 14 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| 영업·사업개발 | 20 | 20 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| AI 응용·에이전트 개발 | 31 | 29 | 2 | 2 | 93.55% | 93.55% | 93.55% |
| ML 플랫폼/MLOps | 24 | 23 | 8 | 1 | 74.19% | 95.83% | 83.64% |
| 클라우드·시스템 인프라 | 33 | 30 | 8 | 3 | 78.95% | 90.91% | 84.51% |
| DBA/데이터베이스 | 4 | 4 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| 임베디드·시스템 SW | 5 | 5 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| 로보틱스·자율주행 | 7 | 7 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| QA·테스트 자동화 | 2 | 2 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| 데이터 분석·BI | 1 | 1 | 0 | 0 | 100.00% | 100.00% | 100.00% |

PM·디자인·영업 등에서 이번 정답과 일치해도 다른 회사/직함/언어에서 같은 결과를 보장하지 않는다. BI 등 표본이 적은 분야는 별도 표본이 필요하다.

## 사이트별 결과

| 사이트 | 입력 공고 | 완전 일치 | 비율 |
| --- | ---: | ---: | ---: |
| 네이버 | 2 | 2 | 100.00% |
| 토스 | 204 | 173 | 84.80% |
| 카카오 | 45 | 41 | 91.11% |
| 쿠팡 | 59 | 42 | 71.19% |
| 당근 | 35 | 31 | 88.57% |
| LINE | 62 | 58 | 93.55% |
| 우아한형제들 | 3 | 3 | 100.00% |
| 오늘의집 | 30 | 28 | 93.33% |

## 남은 불일치의 의미

1. **업무 소유 범위:** MLE가 자신의 모델을 배포하는 일과 ML 플랫폼을 전담 개발하는 일, 배포 플랫폼과 실제 인프라 자원을 소유하는 일을 아직 일부 구분하지 못한다. 동일 불릿의 여러 문장을 함께 읽으면서 다른 대상의 동작이 결합되기도 한다.
2. **업무/역량 구간과 표현 차이:** 세부 직함이 없는 서버 개발, 일본어·중국어 표현, 필수 역량의 누락/구간 오류에서 누락이 남아 있다. 향후 회사별 파서/별칭을 추가하되 ID별 예외로 해결하지 않는다.
3. **현재 업무와 부가 가능성:** 현재 Android 업무와 향후 iOS 가능성, 보조 운영/도구 사용을 독립 직무로 확대하는 사례가 남아 있다.
4. **정답 자료 검토 후보:** 아래 4건은 원본 라벨/설명 사이의 모순 또는 본문 근거 재검토가 필요하다. 규칙을 이 라벨에 억지로 맞추지 않고, 현재 점수에서는 정답을 그대로 사용했다.

## 정답 자료 검토 후보 — 라벨 수정 없음

| 공고 | 고정 정답 | 확인한 문제 | 현재 규칙 |
| --- | --- | --- | --- |
| 603 · 토스뱅크 · FE Platform Engineer | DevOps/SRE | 검토 설명은 프론트엔드와 DevOps 양쪽을 제안하지만 JSON 라벨에는 DevOps만 있음 | DevOps/SRE · 프론트엔드 |
| 604 · 토스 · 프론트엔드 플랫폼 엔지니어 (Frontend Platform Engineer) | DevOps/SRE | 검토 설명은 프론트엔드와 DevOps 양쪽을 제안하지만 JSON 라벨에는 DevOps만 있음 | AI 응용·에이전트 개발 · DevOps/SRE · 프론트엔드 |
| 605 · 토스증권 · 프론트엔드 플랫폼 엔지니어 (Frontend Platform Engineer) | DevOps/SRE | 검토 설명은 프론트엔드와 DevOps 양쪽을 제안하지만 JSON 라벨에는 DevOps만 있음 | 클라우드·시스템 인프라 · DevOps/SRE · 프론트엔드 |
| 633 · 오늘의집 · AI Solutions Engineer | 풀스택 · 웹 백엔드 · 프론트엔드 | 기존 설명은 AI 기능 개발을 우대/관심으로만 봤지만 담당 업무에 “운영 중인 서비스의 장애와 문의에 AI가 대응할 수 있도록 RCA(원인 분석) Agent를 개발하고 지속적으로 튜닝”가 있음 | AI 응용·에이전트 개발 · 풀스택 · 웹 백엔드 · 프론트엔드 |

**이 4건도 제외하거나 수정하지 않고 위 378/440 집계에 포함했다.** 사용자가 정답을 수정하기로 하면 gold-v2를 만들고 별도 평가한다. 기존 표의 설명을 자동으로 정답 태그로 변환하지 않는다.

## 불일치 62개 전부

모든 440개의 정답/예측·구간 모드·규칙 근거는 [전체 결과 JSON](classification-evaluation-v0.5.json)에 있다. 아래 표는 불일치만 빠짐없이 뽑은 것이다. 누락된 직군은 해당 규칙 조건을 통과하지 못한 것이며, 추가 직군의 구절은 실제 통과 경로에서 선택한 짧은 근거다. 이 구절이 있다고 해서 직군 판단이 옳다고 보장하지 않는다.

| ID · 회사 / 공고 | 고정 정답 | 규칙 출력 | 누락(FN) | 추가(FP) | 추가 판정의 짧은 근거 |
| --- | --- | --- | --- | --- | --- |
| 333 · 카카오모빌리티<br>[자율주행 시스템 엔지니어 (R&D)](https://kakaomobility.career.greetinghr.com/ko/o/230974) | 임베디드·시스템 SW · 로보틱스·자율주행 | 클라우드·시스템 인프라 · 임베디드·시스템 SW · 로보틱스·자율주행 | — | 클라우드·시스템 인프라 | 클라우드·시스템 인프라: “차량 및 센서, 인프라 통합설계” |
| 338 · 카카오페이<br>[서버 개발자 - 데이터 플랫폼](https://careers.kakaopay.com/ko/o/235177) | 데이터 엔지니어 · 웹 백엔드 | 웹 백엔드 | 데이터 엔지니어 | — | — |
| 364 · 카카오모빌리티<br>[[Contract] 서버 개발자](https://kakaomobility.career.greetinghr.com/ko/o/228360) | AI 응용·에이전트 개발 · DevOps/SRE | AI 응용·에이전트 개발 · DevOps/SRE · 웹 백엔드 | — | 웹 백엔드 | 웹 백엔드: “대규모 서비스의 통합 모니터링 시스템 개발 및 고도화” |
| 375 · LINE Taiwan<br>[2027 TECH FRESH Spring Class - Alpha Dev(Sticker/FactChecker)](https://careers.linecorp.com/ko/jobs/3131/) | 풀스택 · 웹 백엔드 · 프론트엔드 | DevOps/SRE · 풀스택 · 웹 백엔드 · 프론트엔드 | — | DevOps/SRE | DevOps/SRE: “We are seeking a motivated Software Development Intern to join our team. The successful candidate will actively involve themselves in the fr…” |
| 385 · 토스<br>[AI 솔루션 엔지니어 (AI Solution Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=8000575003) | AI 응용·에이전트 개발 · 웹 백엔드 | AI 응용·에이전트 개발 | 웹 백엔드 | — | — |
| 399 · 토스<br>[AI 엔지니어 (Model) (AI Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=7758217003) | ML 엔지니어 | 데이터 엔지니어 | ML 엔지니어 | 데이터 엔지니어 | 데이터 엔지니어: “실험 기반으로 데이터를 정제하고, 최적의 데이터 분포를 탐색하며, 이를 안정적으로 재현하는 데이터 파이프라인을 구축해요” |
| 400 · 토스증권<br>[AI 엔지니어 (AI Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4%EC%A6%9D%EA%B6%8C&job_id=7825510003) | AI 응용·에이전트 개발 · 데이터 엔지니어 | AI 응용·에이전트 개발 | 데이터 엔지니어 | — | — |
| 401 · LINE Pay Plus<br>[AI/Data Platform Engineer](https://careers.linecorp.com/ko/jobs/3044/) | AI 응용·에이전트 개발 · ML 플랫폼/MLOps · 웹 백엔드 | AI 응용·에이전트 개발 · ML 엔지니어 · ML 플랫폼/MLOps · 웹 백엔드 | — | ML 엔지니어 | ML 엔지니어: “LLM/AI 모델의 긴 추론 시간을 효율적으로 처리하기 위한 비동기 메시지 기반의 메시지 큐(Kafka) 아키텍처 설계 및 운영” |
| 405 · LINE Pay Taiwan<br>[Database Administrator (DBA)_KR-TW](https://careers.linecorp.com/ko/jobs/3046/) | DBA/데이터베이스 | 클라우드·시스템 인프라 · DBA/데이터베이스 | — | 클라우드·시스템 인프라 | 클라우드·시스템 인프라: “MySQL, Redis, HBase 데이터베이스 환경(단일 구성 및 클러스터 환경)의 구축, 운영 및 유지보수” |
| 412 · 토스<br>[AI 플랫폼 엔지니어 (Retrieval) (AI Platform Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=8009061003) | AI 응용·에이전트 개발 · 웹 백엔드 | 미분류 | AI 응용·에이전트 개발 · 웹 백엔드 | — | — |
| 418 · 당근<br>[Software Engineer, Backend - 인프라 (공통 서비스 개발, 콘텐츠 서빙)](https://careers.daangn.com/jobs/role/7644271003/) | 웹 백엔드 | 클라우드·시스템 인프라 | 웹 백엔드 | 클라우드·시스템 인프라 | 클라우드·시스템 인프라: “여러 클라우드와 CDN에 걸친 멀티 CDN 아키텍처를 운영하며, 특정 CDN 장애 시에도 서비스가 멈추지 않도록 킬 스위치 같은 장애 격리 구조를 설계해요” |
| 420 · 당근<br>[Software Engineer, Backend - 피드 (ML Data Platform)](https://careers.daangn.com/jobs/role/6652141003/) | 데이터 엔지니어 · ML 플랫폼/MLOps · 웹 백엔드 | 데이터 엔지니어 · ML 플랫폼/MLOps | 웹 백엔드 | — | — |
| 427 · 토스<br>[AI 플랫폼 엔지니어 (Inference Pipeline) (AI Platform Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=7986152003) | AI 응용·에이전트 개발 · ML 플랫폼/MLOps | AI 응용·에이전트 개발 · DevOps/SRE | ML 플랫폼/MLOps | DevOps/SRE | DevOps/SRE: “Rate Limit과 이중화, 장애 전환, Fallback/Retry 등을 설계해 운영 안정성을 확보해요.” |
| 429 · 당근<br>[Software Engineer, Backend - 피드 (피드 인프라)](https://careers.daangn.com/jobs/role/5046757003/) | 웹 백엔드 | 미분류 | 웹 백엔드 | — | — |
| 452 · 쿠팡<br>[Senior Staff Backend Engineer - (K8)](https://www.coupang.jobs/en/jobs/?gh_jid=7912287) | 클라우드·시스템 인프라 | 클라우드·시스템 인프라 · 웹 백엔드 | — | 웹 백엔드 | 웹 백엔드: “As a Senior Staff Backend Engineer you will partner with leaders of multiple platform teams. You will work closely with the product team to …” |
| 454 · 토스<br>[Forward Deployed Engineer ](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=7996773003) | AI 응용·에이전트 개발 · 웹 백엔드 | AI 응용·에이전트 개발 | 웹 백엔드 | — | — |
| 468 · 당근<br>[Software Engineer, Machine Learning - 검색 (품질)](https://careers.daangn.com/jobs/role/6610455003/) | ML 엔지니어 | ML 엔지니어 · ML 플랫폼/MLOps | — | ML 플랫폼/MLOps | ML 플랫폼/MLOps: “모델의 실시간 서빙을 위한 경량화 및 아키텍처를 설계해요” |
| 469 · LY Corporation<br>[Androidエンジニア / Yahoo!カレンダー](https://www.lycorp.co.jp/ja/recruit/career/job-categories/ly00583/) | Android | Android · iOS | — | iOS | iOS: “「Yahoo!カレンダー」は、「LINE」や「Yahoo! JAPAN」アプリと連携し、LINEヤフーならではの新しいカレンダー体験を目指してサービス開発を行っています。本ポジションでは、ネイティブアプリエンジニアとして、「Yahoo!カレンダー」の開発・運用に取り組んでいただき…” |
| 470 · 쿠팡<br>[Senior Staff ll, AI Engineer (L7-2)](https://www.coupang.jobs/en/jobs/?gh_jid=7834206) | AI 응용·에이전트 개발 · ML 플랫폼/MLOps | AI 응용·에이전트 개발 · 데이터 엔지니어 · ML 플랫폼/MLOps | — | 데이터 엔지니어 | 데이터 엔지니어: “Develop and manage production AI infrastructure, including vector databases, high-throughput inference, and real-time data pipelines for con…” |
| 473 · 쿠팡<br>[Senior Staff ll, Machine Learning Engineer - Search Relevance](https://www.coupang.jobs/en/jobs/?gh_jid=8090443) | ML 엔지니어 | 미분류 | ML 엔지니어 | — | — |
| 475 · 토스증권<br>[ML 엔지니어 (LLM) (ML Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4%EC%A6%9D%EA%B6%8C&job_id=7575603003) | ML 엔지니어 | ML 엔지니어 · ML 플랫폼/MLOps | — | ML 플랫폼/MLOps | ML 플랫폼/MLOps: “실제 서비스 환경에 모델을 서빙하고 안정적으로 운영해요.” |
| 479 · 토스<br>[ML 엔지니어 (OCR) (ML Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=8035179003) | ML 엔지니어 | DevOps/SRE · ML 엔지니어 | — | DevOps/SRE | DevOps/SRE: “글로벌 eKYC를 위해 국가별 학습 데이터 의존도를 낮춘 일반화 모델(KIE · Quality Checker · Fraud Detection)을 설계하고, 재사용 가능한 학습 · 평가 · 배포 파이프라인을 만들어요.” |
| 482 · 쿠팡<br>[Sr. Staff Backend Engineer](https://www.coupang.jobs/en/jobs/?gh_jid=8275829) | 데이터 엔지니어 | 데이터 엔지니어 · 웹 백엔드 | — | 웹 백엔드 | 웹 백엔드: “The ideal candidate is a backend engineering expert with extensive experience in building scalable data systems, excellent problem-solving s…” |
| 490 · 토스<br>[ML 엔지니어 (Search Relevance) (ML Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=7992223003) | ML 엔지니어 | 데이터 엔지니어 · ML 엔지니어 | — | 데이터 엔지니어 | 데이터 엔지니어: “Light/Heavy 2-tier 구조 전반의 학습 데이터 파이프라인을 직접 설계해요. 로그·행동 데이터에서 학습셋을 구성하고, LLM 레이블링 프롬프트와 휴먼 레이블링 루프를 함께 운영해요.” |
| 494 · 쿠팡<br>[Sr. Staff Back-End Engineer](https://www.coupang.jobs/en/jobs/?gh_jid=7999712) | 웹 백엔드 | 미분류 | 웹 백엔드 | — | — |
| 496 · 토스<br>[ML 엔지니어 (Commerce Recommendation) (ML Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=6543801003) | ML 엔지니어 | ML 엔지니어 · ML 플랫폼/MLOps | — | ML 플랫폼/MLOps | ML 플랫폼/MLOps: “예측 모델의 결과가 실제 추천 랭킹과 서빙 로직에 안정적으로 반영될 수 있도록 score calibration, 리랭킹, 다양성, 신선도, 탐색 전략을 설계해요.” |
| 497 · 쿠팡페이<br>[Sr. Staff Back-end Engineer (Coupang Pay)](https://www.coupang.jobs/en/jobs/?gh_jid=7936538) | AI 응용·에이전트 개발 · 풀스택 · 웹 백엔드 · 프론트엔드 | AI 응용·에이전트 개발 · 데이터 엔지니어 · 풀스택 · 웹 백엔드 · 프론트엔드 | — | 데이터 엔지니어 | 데이터 엔지니어: “Architect, design, and implement complex, distributed, cloud-native systems spanning frontend, backend, APIs, data pipelines, and infrastruc…” |
| 498 · 토스<br>[ML 엔지니어 (Commerce Ads) (ML Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=7784598003) | ML 엔지니어 | ML 엔지니어 · ML 플랫폼/MLOps | — | ML 플랫폼/MLOps | ML 플랫폼/MLOps: “CTR/CVR 예측 모델을 담당하는 팀 내 MLE들과 긴밀하게 협업하고, 데이터 분석가·서버 엔지니어·프로덕트 매니저와 함께 모델의 예측 결과가 광고비 계산, 랭킹, 서빙 시스템에 안정적으로 반영되도록 만들어요.” |
| 504 · 쿠팡<br>[Sr. Staff, Machine Learning Engineer (Search & Discovery)](https://www.coupang.jobs/en/jobs/?gh_jid=8130040) | ML 엔지니어 | ML 엔지니어 · ML 플랫폼/MLOps | — | ML 플랫폼/MLOps | ML 플랫폼/MLOps: “As a Sr. Staff Machine Learning Engineer, you will drive the design, development, and evolution of our end-to-end recommendation systems, pl…” |
| 507 · 토스<br>[Node.js 개발자 (Node.js Developer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=7799519003) | AI 응용·에이전트 개발 · 데이터 엔지니어 | 데이터 엔지니어 | AI 응용·에이전트 개발 | — | — |
| 517 · 쿠팡<br>[Staff Back-end Engineer](https://www.coupang.jobs/en/jobs/?gh_jid=7892757) | 웹 백엔드 | 데이터 엔지니어 · 웹 백엔드 | — | 데이터 엔지니어 | 데이터 엔지니어: “As a Staff Software Engineer, Backend, you will work on distributed systems, data processing pipelines and building next generation platform…” |
| 522 · 토스<br>[서버 개발자 (Platform) (Server Developer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=7977821003) | DevOps/SRE · 웹 백엔드 | 클라우드·시스템 인프라 · DevOps/SRE · 웹 백엔드 | — | 클라우드·시스템 인프라 | 클라우드·시스템 인프라: “Server Platform, Messaging Platform, SRE 세 개 팀으로 구성되어 있어요. 새로운 클러스터 구축과 신규 기술 도입, 장애를 미리 발견하고 막는 모니터링 고도화, 토스커뮤니티 전반의 메시지 전달까지 폭넓은 영역을 다뤄요. …” |
| 523 · 토스<br>[서버 개발자 (생산성) (Server Developer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=6288478003) | DevOps/SRE · 웹 백엔드 | DevOps/SRE | 웹 백엔드 | — | — |
| 527 · 토스페이먼츠<br>[서버 개발자 (3년 이하) (Server Developer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4%ED%8E%98%EC%9D%B4%EB%A8%BC%EC%B8%A0&job_id=5571302003) | 웹 백엔드 | 미분류 | 웹 백엔드 | — | — |
| 528 · 쿠팡페이<br>[Staff Backend Engineer (Fintech Data Platform)](https://www.coupang.jobs/en/jobs/?gh_jid=8302736) | 웹 백엔드 | 데이터 엔지니어 · DevOps/SRE · 웹 백엔드 | — | 데이터 엔지니어 · DevOps/SRE | 데이터 엔지니어: “비즈니스 성장에 맞춰 효율적으로 확장할 수 있는 API, 데이터 모델 및 서비스 아키텍처 설계”<br>DevOps/SRE: “시스템 성능, 가시성(Observability), 복원력(Resiliency) 및 운영 탁월성(Operational Excellence) 향상” |
| 529 · 토스페이먼츠<br>[서버 개발자 (TCP·전문통신) (Server Developer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4%ED%8E%98%EC%9D%B4%EB%A8%BC%EC%B8%A0&job_id=7980054003) | 웹 백엔드 | DevOps/SRE | 웹 백엔드 | DevOps/SRE | DevOps/SRE: “빌드, 배포, 변경통제, 디버깅, 모니터링, 관제, 알림 등의 운영 절차를 자동화해요.” |
| 531 · 쿠팡<br>[Staff Backend Engineer - Intelligent Cloud](https://www.coupang.jobs/en/jobs/?gh_jid=7916740) | 웹 백엔드 | 클라우드·시스템 인프라 · 웹 백엔드 | — | 클라우드·시스템 인프라 | 클라우드·시스템 인프라: “Collaborate with product, infrastructure, and platform teams to build cloud-native commerce capabilities” |
| 532 · 쿠팡<br>[Staff Back-end Engineer [L6-1] (GenAI Creative)](https://www.coupang.jobs/en/jobs/?gh_jid=8242187) | AI 응용·에이전트 개발 · 웹 백엔드 | AI 응용·에이전트 개발 | 웹 백엔드 | — | — |
| 534 · 쿠팡<br>[Staff Back-end Engineer [L6-1] (NCA Services)](https://www.coupang.jobs/en/jobs/?gh_jid=8302512) | ML 엔지니어 · ML 플랫폼/MLOps · 웹 백엔드 | ML 엔지니어 · ML 플랫폼/MLOps | 웹 백엔드 | — | — |
| 535 · 토스뱅크<br>[서버 개발자 (Product Foundation) (Server Developer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4%EB%B1%85%ED%81%AC&job_id=4542935003) | 웹 백엔드 | ML 엔지니어 · 웹 백엔드 | — | ML 엔지니어 | ML 엔지니어: “룰·시나리오와 ML 모델을 결합해 이상거래를 실시간으로 찾아내는 이상거래 탐지 시스템(FDS)을 고도화해요.” |
| 540 · 토스플레이스<br>[서버 개발자 (Platform) (Server Developer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4%ED%94%8C%EB%A0%88%EC%9D%B4%EC%8A%A4&job_id=5191524003) | 웹 백엔드 | DevOps/SRE · 웹 백엔드 | — | DevOps/SRE | DevOps/SRE: “담당 플랫폼의 진단에 필요한 관측 데이터를 제공하고, 장애 대응·복구·성능 개선과 재발 방지를 책임져요. DevOps, SRE, DBA와 함께 여러 계층에 걸친 문제를 해결하고, 주요 장애에서 얻은 학습을 담당 플랫폼의 코드와 운영 방식에 반영해요.” |
| 560 · 토스<br>[데이터 분석 엔지니어 (DW/Global) (Data Analytics Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=7985496003) | 데이터 엔지니어 | 데이터 엔지니어 · 웹 백엔드 | — | 웹 백엔드 | 웹 백엔드: “서비스 DB 스냅샷, 앱·서버 로그, 외부 API 등 수십 종의 원천을 Snowflake 기반 Data Warehouse로 수집하는 입수 레이어를 설계하고 운영해요.” |
| 561 · 토스뱅크<br>[데이터 엔지니어 (Platform) (Data Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4%EB%B1%85%ED%81%AC&job_id=4695176003) | 클라우드·시스템 인프라 · 데이터 엔지니어 | 데이터 엔지니어 | 클라우드·시스템 인프라 | — | — |
| 564 · 토스인컴<br>[데이터 엔지니어 (Data Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4%EC%9D%B8%EC%BB%B4&job_id=6304668003) | 데이터 엔지니어 | 데이터 엔지니어 · 웹 백엔드 | — | 웹 백엔드 | 웹 백엔드: “토스인컴 팀원들이 데이터를 더 쉽고 안전하게 활용할 수 있도록 API, 자동화 도구, 플랫폼 기능을 직접 개발해요.” |
| 566 · 토스증권<br>[데이터 엔지니어 (DataOps) (Data Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4%EC%A6%9D%EA%B6%8C&job_id=8006356003) | 클라우드·시스템 인프라 · 데이터 엔지니어 · DevOps/SRE | 데이터 엔지니어 · DevOps/SRE | 클라우드·시스템 인프라 | — | — |
| 569 · 쿠팡페이<br>[[쿠팡페이] Staff iOS Engineer (Core Payment)](https://www.coupang.jobs/en/jobs/?gh_jid=8345064) | iOS | iOS · 웹 백엔드 | — | 웹 백엔드 | 웹 백엔드: “Product Manager, Designer, Backend Engineer 등 다양한 이해관계자와 협업하여 최고의 사용자 경험을 제공합니다.” |
| 571 · 토스<br>[데이터 엔지니어 (Global) (Data Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=7987773003) | 클라우드·시스템 인프라 · 데이터 엔지니어 · DevOps/SRE | 데이터 엔지니어 · DevOps/SRE | 클라우드·시스템 인프라 | — | — |
| 573 · 쿠팡페이<br>[[쿠팡페이] Staff iOS Engineer (Rocket Pay)](https://www.coupang.jobs/en/jobs/?gh_jid=8146512) | iOS | iOS · 웹 백엔드 | — | 웹 백엔드 | 웹 백엔드: “Product Manager, Designer, Backend Engineer 등 다양한 이해관계자와 협업하여 최고의 사용자 경험을 제공합니다.” |
| 578 · 토스뱅크<br>[데이터 플랫폼 엔지니어 (Streaming) (Data Platform Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4%EB%B1%85%ED%81%AC&job_id=6603749003) | 데이터 엔지니어 | 데이터 엔지니어 · DevOps/SRE | — | DevOps/SRE | DevOps/SRE: “서버/서비스/앱 로그 등 대용량 메시지 처리를 위한 Kafka, Kafka Connect 기반의 실시간 Data Platform을 운영해요.” |
| 585 · 토스<br>[서버 개발자 (토스 자회사/3년 이하) (Server Developer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=8047064003) | 웹 백엔드 | 미분류 | 웹 백엔드 | — | — |
| 586 · 쿠팡<br>[Staff Machine Learning Engineer, Offsite Ads](https://www.coupang.jobs/en/jobs/?gh_jid=8356962) | ML 엔지니어 | ML 엔지니어 · ML 플랫폼/MLOps | — | ML 플랫폼/MLOps | ML 플랫폼/MLOps: “Build scalable ML infrastructure and production systems that enable rapid experimentation and reliable model deployment.” |
| 589 · 쿠팡<br>[Staff, Machine Learning Engineer (Search & Discovery)](https://www.coupang.jobs/en/jobs/?gh_jid=8130045) | ML 엔지니어 | ML 엔지니어 · ML 플랫폼/MLOps | — | ML 플랫폼/MLOps | ML 플랫폼/MLOps: “As a Staff Machine Learning Engineer, you will drive the design, development, and evolution of our end-to-end recommendation systems, placin…” |
| 590 · 토스인슈어런스<br>[시스템 엔지니어 (Systems Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4%EC%9D%B8%EC%8A%88%EC%96%B4%EB%9F%B0%EC%8A%A4&job_id=7510564003) | 클라우드·시스템 인프라 | 클라우드·시스템 인프라 · DevOps/SRE | — | DevOps/SRE | DevOps/SRE: “Kubernetes 운영 환경, IaC(Terraform/Ansible), Observability 체계 구축 등 인프라 자동화 및 고도화 업무에도 함께 참여할 수 있어요.” |
| 591 · 쿠팡페이<br>[Staff Software Engineer, AI Platform (GenAI & Agent Infrastructure)](https://www.coupang.jobs/en/jobs/?gh_jid=8382373) | AI 응용·에이전트 개발 · 웹 백엔드 | AI 응용·에이전트 개발 · 클라우드·시스템 인프라 · ML 플랫폼/MLOps · 웹 백엔드 | — | 클라우드·시스템 인프라 · ML 플랫폼/MLOps | 클라우드·시스템 인프라: “클라우드 환경 내 프로덕션 AI 서비스 모니터링 및 안정적 운영”<br>ML 플랫폼/MLOps: “에이전트가 사내 지식 소스를 검색·처리하고 이를 바탕으로 추론할 수 있는 환경 구현” |
| 598 · 토스<br>[프론트엔드 개발자 (Frontend Developer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=4071101003) | 프론트엔드 | DevOps/SRE · 프론트엔드 | — | DevOps/SRE | DevOps/SRE: “단 1분 만에 프론트엔드 웹 서비스가 빌드되고 배포되는 환경처럼, 최고의 사용감을 위해 프론트엔드 개발 환경을 개선하고 있어요.” |
| 603 · 토스뱅크<br>[FE Platform Engineer](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4%EB%B1%85%ED%81%AC&job_id=5622017003) | DevOps/SRE | DevOps/SRE · 프론트엔드 | — | 프론트엔드 | 프론트엔드: “프론트엔드 어드민을 구축해서 배포를 좀 더 편하게 할 수 있게 하고, 리다이렉트 로직이 필요한 경우 이 어드민에서 설정할 수 있게 했어요. DX를 위한 기능을 이 어드민에 추가하고 있어요.” |
| 604 · 토스<br>[프론트엔드 플랫폼 엔지니어 (Frontend Platform Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4&job_id=7545902003) | DevOps/SRE | AI 응용·에이전트 개발 · DevOps/SRE · 프론트엔드 | — | AI 응용·에이전트 개발 · 프론트엔드 | AI 응용·에이전트 개발: “프론트엔드 개발자들이 AI를 더 쉽게 설정하고, 사내 메신저나 사내 시스템을 AI로 활용할 수 있도록 하는 인프라를 만들어요.”<br>프론트엔드: “프론트엔드 개발자들이 AI를 더 쉽게 설정하고, 사내 메신저나 사내 시스템을 AI로 활용할 수 있도록 하는 인프라를 만들어요.” |
| 605 · 토스증권<br>[프론트엔드 플랫폼 엔지니어 (Frontend Platform Engineer)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4%EC%A6%9D%EA%B6%8C&job_id=5130165003) | DevOps/SRE | 클라우드·시스템 인프라 · DevOps/SRE · 프론트엔드 | — | 클라우드·시스템 인프라 · 프론트엔드 | 클라우드·시스템 인프라: “AWS 등의 클라우드 서비스를 적극적으로 활용해 토스증권의 자체 배포 인프라를 구축하고 관리해요.”<br>프론트엔드: “유저가 네이티브 화면과 웹뷰 화면을 오갈 때 자연스러운 경험을 줄 수 있으면서, 네이티브와 웹뷰 플랫폼 간 호환 문제에서 자유로운 개발 환경을 구축해요.” |
| 617 · 카카오엔터프라이즈<br>[[인프라실] Database Engineer 영입](https://careers.kakaoenterprise.com/ko/o/242480) | DBA/데이터베이스 | 클라우드·시스템 인프라 · DBA/데이터베이스 | — | 클라우드·시스템 인프라 | 클라우드·시스템 인프라: “계열사 데이터베이스 운영 및 카카오클라우드 이전 지원” |
| 633 · 오늘의집<br>[AI Solutions Engineer](https://bucketplace.career.greetinghr.com/ko/o/234818) | 풀스택 · 웹 백엔드 · 프론트엔드 | AI 응용·에이전트 개발 · 풀스택 · 웹 백엔드 · 프론트엔드 | — | AI 응용·에이전트 개발 | AI 응용·에이전트 개발: “운영 중인 서비스의 장애와 문의에 AI가 대응할 수 있도록 RCA(원인 분석) Agent를 개발하고 지속적으로 튜닝” |
| 646 · 오늘의집<br>[Backend Engineer, Interior & Life](https://bucketplace.career.greetinghr.com/ko/o/199336) | 웹 백엔드 | 미분류 | 웹 백엔드 | — | — |
| 683 · 토스증권<br>[IT 기획 매니저 (데이터 변경통제) (IT Planning Manager)](https://toss.im/career/job-detail?company=%ED%86%A0%EC%8A%A4%EC%A6%9D%EA%B6%8C&job_id=7984098003) | 기획·전략 | 데이터 엔지니어 · 기획·전략 | — | 데이터 엔지니어 | 데이터 엔지니어: “토스증권의 성장과 안정성을 모두 아우르는 데이터 운영 환경을 존중하면서도, 데이터베이스·데이터 파이프라인 전반의 변경 사항이 내외부 감사 기준을 충족할 수 있도록 실효성 있는 데이터 변경 통제 체계를 설계해요.” |

## 재현 방법

Python 표준 라이브러리만 필요하다. 본문 스냅샷 JSONL 한 줄의 형식은 `id`, `source`, `company`, `title`, `body`를 포함하는 객체다. 440개 ID 집합과 본문 해시·메타데이터가 gold-v1에 일치해야 하며, 현재 DB에 새 데이터가 들어왔다면 당시 저장 버전을 기준으로 스냅샷을 준비해야 한다. 본문 전체는 저장소에 복제하지 않았다.

```sh
python3 scripts/analysis/evaluate-classification-rules.py \
  --corpus /tmp/jobmoa-corpus-440.jsonl \
  --rules backend/src/main/resources/classification/rules-v0.5.json \
  --gold docs/analysis/classification-440-2026-11-15.json \
  --manifest docs/analysis/classification-gold-v1.json \
  --output /tmp/jobmoa-evaluation-replay.json
```

도구는 판정 함수에 제목·본문만 전달하고, 결과가 나온 뒤 ID로 정답을 연결한다. 결과 파일 쓰기 외에는 DB/네트워크 작업을 하지 않는다. 고정 정답 파일이 바뀌면 SHA-256 검증으로 중단한다. 제품에 적용할 때는 Kotlin 구현과 이 명세의 결과를 별도로 대조해야 한다.

## 검증 식별자

- 정답 SHA-256: `3c2b069746856baa9ae4133e978477abb641675871ba04b9ebf6f33fa9fca927`
- 규칙 SHA-256: `19541f058ba1a767ef5acf50e053d3d5d0d0f83f2e45be5f28a1fdc79c5d07ed`
- 본문 스냅샷 SHA-256: `6456da9a8f722e2e3bcd7937c9734e557a6b04a86d54b8830a9347d184d9655f`
- 평가 도구 SHA-256: `cea88193f78ec0c471c01a9f5a1cc60b125498c306dfea46b5f23abb1c5473de`

검증 범위는 고정된 440개 전수 평가, 입력 해시·ID·메타데이터 검증, 지표/불일치 집계 재현이다. 애플리케이션 빌드·실제 수집·화면 검증은 이번 규칙 평가 범위에 포함하지 않았다.

제품 승격 후 규칙 원본은 백엔드 리소스로 이동했고 버전/상태만 0.5로 변경했다. 이 문서와 JSON의 기존 SHA·v0.5-draft 표기는 당시 평가 이력이며, 조건 동일성/실제 Kotlin 대조는 [구현 검증 기록](classification-implementation-v0.5.md)을 따른다.
