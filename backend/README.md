# 백엔드

Kotlin · Spring Boot · Spring Data JPA · MySQL 8.4. Java 21 이상을 사용합니다.

```sh
# 루트에서 DB만 실행
docker compose up -d --wait mysql
cd backend
BROWSER_CHANNEL=chrome ./gradlew bootRun
```

로컬 실행은 설치된 Chrome을 재사용합니다. Playwright 내장 Node가 구형 macOS에서 실행되지 않으면 `PLAYWRIGHT_NODEJS_PATH`에 실행 가능한 Node 22 경로를 지정하세요. Docker에는 Chromium 전체 브라우저·headless shell·한글 글꼴·Xvfb 가상 화면이 포함됩니다.

API: `http://127.0.0.1:18080`, 상태 확인: `/actuator/health`.
전체 Docker 시작·종료는 루트의 `./start.sh`, `./stop.sh`를 사용합니다.

도메인별로 `domain`, `application`, `infrastructure`, `presentation`을 나눕니다.
도메인 모델은 JPA 엔티티와 분리하며, 저장소 구현체에서 Spring Data 저장소를 호출하고 변환합니다.

- `PostingJpaRepository`: URL 해시 잠금 조회, 최초 발견일·ID 내림차순 목록.
- `PostingVersionJpaRepository`: 공고별 버전 조회.
- 수집 실행·기업별 발견 작업·공고 작업도 Spring Data JPA 저장소를 사용합니다.
- 일반 CRUD는 `JpaRepository` 기본 메서드와 파생 쿼리로 처리합니다. 큐 선점의 `FOR UPDATE SKIP LOCKED`, 원자적 중복 삽입, 일괄 임대 갱신·복구는 별도 쿼리를 선언합니다.

`TaskService`의 각 메서드가 짧은 트랜잭션 경계입니다. 본문·캡처를 넘겨받아 체크포인트를 저장하고, 최종 저장에서는 작업 잠금 → 공고 잠금 → 공고·버전 저장 → 작업 완료를 하나로 처리합니다.
브라우저 탐색·본문 추출·스크린샷은 서비스 호출 전에 수행하는 구조이며, DB 트랜잭션 안에서 실행하지 않습니다. 읽음 변경도 공고 잠금을 사용합니다.

참고: [Spring Data 잠금](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html), [트랜잭션](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html).

MySQL 격리 수준은 `READ_COMMITTED`입니다. 여러 작업자가 서로 다른 새 공고를 저장할 때 존재하지 않는 URL 해시의 구간 잠금이 충돌하는 것을 피합니다. `useAffectedRows=true`로 큐 중복 삽입의 영향 행 수를 0으로 구분합니다.

현재 제공하는 기반 API:

| 메서드 | 경로 | 기능 |
| --- | --- | --- |
| GET | `/api/statistics` | 사이트·회사·직무·기간별 관측 통계 |
| GET | `/api/state` | 공고·수집 기록·수집기 지원 상태 |
| GET | `/api/postings` | 공고 목록 및 저장 버전 |
| GET | `/api/postings/{id}` | 공고 상세 |
| PATCH | `/api/postings/{id}/read` | `{ "read": true }` |
| GET | `/api/collections` | 최근 실행 30개 |
| GET | `/api/collections/{id}` | 기업별 결과와 실패 항목 |
| GET | `/api/collections/{id}/tasks` | 작업별 상태·실패 단계 |
| POST | `/api/collections` | `{ "sourceIds": ["kakao"], "categoryId": "web-backend", "companyIds": ["kakaostyle"] }` |
| POST | `/api/collections/{id}/retry` | `{ "failureIds": ["..."] }`, 생략 시 미해결 실패 전체 |

수집기는 8개 사이트·기업군별로 구현되어 있습니다. 사이트 어댑터가 공식 직군 등으로 후보를 추리고, `CategoryClassifier`가 회사 × 분야별 제목과 본문 근거를 확인합니다. 공통 실행부에는 개발 직군 키워드 필터가 없습니다.

- 네이버: 통합·클라우드·SNOW/KREAM·랩스·웹툰·파이낸셜·I&S·Z.
- 토스: 통합 포털의 실제 계열사별 개별 포지션.
- 카카오: 본사, 페이·뱅크·페이증권·모빌리티·엔터프라이즈·엔터테인먼트·스타일·VX·페이손해보험·디케이테크인·키즈노트 별도 포털.
- 쿠팡: 그룹 공식 Greenhouse 전체 보드. 상세 공고는 일반 Chromium 모드와 컨테이너 내부 Xvfb 가상 화면으로 렌더링합니다. 본문은 공식 공개 API로 조회하고 실제 화면의 제목·본문과 대조하며, API 요청에도 호스트별 제한을 적용합니다. 보안 확인이나 접근 제한이 발생하면 실패로 남기며 해당 화면을 공고로 저장하지 않습니다.
- 당근: 당근·당근페이 통합, 당근서비스, 북미·일본 포털.
- LINE: LINE Careers 전 지역, LY 일본, LY Communications.
- 우형: 우아한형제들·우아한청년들.
- 오늘의집: 버킷플레이스 공식 채용 목록과 Greeting 상세. 개발뿐 아니라 기획·PM·디자인·마케팅·영업을 지원합니다. [수집 규칙](../docs/source-bucketplace.md).

이 목록은 구현된 공개 포털 범위이며 모든 법인의 존재/채용 여부를 보증하는 법인 목록은 아닙니다. 최신 등록 범위는 `/api/sources`의 `portals`에서 확인합니다. 공고가 0건인 포털도 정상 수집 결과이며, 구조 변경은 실패로 기록합니다.

실행 버튼 한 번에 기록 하나를 만들고, 각 계열사 포털의 목록 조회는 독립 작업으로 저장합니다. 한 포털이 실패해도 다른 포털과 공고 처리는 계속하며, 목록 재시도는 실패한 포털만 다시 실행합니다. 공고는 공통 MySQL 큐에 넣고 빈 작업자가 가져갑니다.

작업자는 기본 3개(`CRAWLER_WORKERS`), 사이트별 활성 페이지 작업은 기본 1개(`CRAWLER_SITE_CONCURRENCY`)입니다. 별도 목록 탐색 스레드 1개도 사이트 제한을 공유합니다. 이는 페이지 작업 제한이며 이미지·CSS 등 모든 하위 리소스 요청의 개수 제한은 아닙니다. `site-overrides` 설정으로 호스트별 한도를 바꿀 수 있습니다.

각 작업자는 자기 스레드에서만 Playwright와 브라우저를 생성·재사용·정리합니다. 작업별 컨텍스트는 분리합니다. 쿠팡 상세만 일반 브라우저 모드를 사용하며, 다른 모드의 작업으로 전환할 때 기존 브라우저를 정리해 작업자당 브라우저 하나를 유지합니다. Java는 컨테이너 PID 1로 실행해 종료 신호를 직접 받습니다. 임대 토큰과 주기적 갱신으로 중복 처리를 방지하고, 중단된 공고 작업은 임대 만료 후 체크포인트부터 다시 처리합니다. 브라우저 탐색·본문·캡처는 DB 트랜잭션 밖에서 수행합니다.

목록·진행률 조회에는 본문을 포함하지 않습니다. `/api/postings/{id}`에서 본문을 읽고, 캡처는 `/api/captures/{해시}.png`에서 선택적으로 불러옵니다. 변경된 공고만 새 버전을 저장하고 이전 버전은 보관합니다. 저장된 진행 중 공고도 다음 수집에 다시 확인하며, 명시적인 마감만 보관 처리합니다.

설정은 `src/main/resources/application.yaml`을 참고하세요. DB 스키마는 Flyway가 관리하며 JPA는 `validate`만 수행합니다. DB Docker 볼륨과 `backend/storage/captures`는 일반 종료 시 삭제하지 않습니다.


### 분야별 수집

`/api/state`의 `categories`와 `companies`에서 지원 분야·계열사 조합을 확인합니다. `all`은 각 사이트 어댑터가 지원하는 전체 공고이며 별도 공고 태그로 저장하지 않습니다. 토스·당근 국내·오늘의집은 비개발도 지원하고 다른 포털의 개발 범위는 유지합니다. 개발 분야는 웹 백엔드·프론트엔드·Android·iOS·풀스택·데이터 엔지니어·ML 엔지니어·DevOps/SRE이며, 기획·PM·디자인·마케팅·영업은 토스 9개 계열사·당근·당근페이·오늘의집에서 지원합니다. [회사별 규칙](../docs/non-development-sources.md). 분야별 후보 탐색은 회사별 전략을 유지하고, 최종 본문 판정과 저장 직군은 일반 규칙 v0.5를 적용합니다. 전체 63개 회사 × 분야 지원표와 제외 기준은 [분야 문서](../docs/job-categories.md), 기존 어댑터의 선행 필터는 [네이버·토스·카카오](../docs/adapter-role-coverage.md), [쿠팡·당근·LINE·우형](../docs/adapter-role-coverage-extra.md)을 참고하세요.

회사 카탈로그는 `CompanyRegistry`, 회사 × 분야 판별은 `CategoryClassifier`에 선언합니다. 통합 포털은 한 번 탐색하고 선택한 계열사의 공고만 큐에 넣습니다. 수집 실행과 작업에 분야·계열사 정보를 저장하므로 재시도에도 동일한 범위가 유지됩니다.

`posting_category`는 공고와 분야의 다중 연결입니다. 다른 분야에서 같은 URL의 공고를 발견해도 원문 공고는 공유합니다. 회사·제목·근무지·정규화 본문이 기존 최신 버전과 같으면 `reused_version_id`로 기존 캡처를 재사용하고 곧바로 SAVE 단계로 넘어갑니다. 마감 상태만 바뀌어도 본문·캡처 버전을 추가하지 않고 공고 상태만 갱신합니다. 마감 상태를 포함했던 이전 해시도 호환 비교하므로 기존 버전은 유지합니다. 완료 작업의 임시 본문은 비워 본문 원본은 버전에만 보관합니다.

기존 웹 백엔드 기능 검증 당시(2026-11-14): 카카오스타일 전체 5건 → 웹 백엔드 동일 4건(본문·캡처 추가 저장 0, MLOps 제외) → 쿠팡파이낸셜 웹 백엔드 신규 1건. 세 실행 모두 성공했으며 당시 공고·버전·캡처는 각각 6개였습니다. 현재 데이터 수를 의미하지 않습니다. 등록된 전체 회사에 대한 실수집을 반복하지 않고 표본 계열사로 검증했습니다.

직무 확장·오늘의집 추가 후 2026-11-14 소량 버튼 검증은 카카오페이증권 DevOps/SRE, 오늘의집 PM, 토스 Android를 대상으로 했다. 상세 결과와 검증하지 않은 범위는 [분야 문서](../docs/job-categories.md#실제-버튼-검증--2026-11-14)에 기록한다.


### 관측 통계

`analytics` 도메인의 `StatisticsQuery`가 목록 메타데이터와 상태 관측만 읽어 통계를 계산합니다. 읽기 전용 REPEATABLE_READ 트랜잭션으로 공고·관측의 일관된 스냅샷을 사용합니다. 본문·캡처를 읽거나 외부 수집을 시작하지 않습니다.

Flyway V6은 기존 공고의 마지막 상태를 기준점으로 보존합니다. 이후 `TaskService.finish`가 공고·버전·작업 완료와 관측을 한 트랜잭션으로 저장하며, 동일 본문 재사용도 관측을 남깁니다. 실패·제외는 성공한 관측으로 세지 않습니다. 마감·재오픈 전이, 미완료 기간 제외, 표본 없는 평균의 `null` 처리, 필터·기간 정의는 [채용 통계](../docs/statistics.md)를 따릅니다.

## 자동 직군 판정과 저장 본문 재분류

`classification/domain`의 `PostingClassifier` 계약, `classification/infrastructure/RulePostingClassifier`가 단일 [JSON 규칙](src/main/resources/classification/rules-v0.5.json)을 해석합니다. 정답 ID/URL/공고명 목록은 런타임에 포함하지 않습니다. 본문 구역 추출과 문장 단위 조건·직함 보강·직군 의존 순서를 사용합니다. 전체 21개 직군의 포함·제외는 [분류 규칙](../docs/classification-rules.md)을 참고하세요. 새 8개 분류 분야는 회사별 개별 수집을 지원한다고 표시하지 않습니다.

`TaskService.finish`는 변경/동일 여부와 선택한 수집 분야에 관계없이 전체 직군을 판정합니다. 동일 본문은 저장 버전을 재사용합니다. `posting_category` 연결을 결과 집합과 일치시키므로 이전 오분류도 제거됩니다. `posting.classification_json`에는 규칙 버전·판정 시각·입력 해시·본문 구역 모드·직군별 근거 문장·미분류 이유가 저장됩니다. 본문 버전이 아닌 최신 공고의 현재 분류입니다.

- `POST /api/classifications/rebuild`: 저장된 모든 공고의 최신 본문만 재분류. 외부 접속·본문/캡처 추가 없음. 응답: `requested`, `completed`, `failedPostingIds`, `unclassified`, `assignments`.
- `GET /api/state`, `/api/postings`, `/api/postings/{id}`: 각 공고의 `categories`(복수 가능)와 `classification` 제공. 아직 분류하지 않은 과거 데이터는 `classification: null`.
- 공고별 비관적 잠금과 짧은 트랜잭션으로 최신 본문을 읽고 연결을 교체합니다. 수집·읽음 변경과 같은 공고 잠금을 사용하며, 개별 실패는 목록으로 반환합니다. 재분류는 브라우저를 만들지 않습니다.

실제 Kotlin 결과 평가 도구: `java -cp 'backend/build/classes/kotlin/main:backend/build/resources/main:backend/build/runtime-libs/*' scripts/analysis/EvaluateKotlinClassifier.java /path/corpus.jsonl /tmp/predictions.json` (루트에서 빌드 후 JDK 21 이상). 고정 정답과 Python 해석기 대조 결과는 [구현 검증](../docs/analysis/classification-implementation-v0.5.md)에 기록합니다.
