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

수집기는 17개 사이트·기업군별로 구현되어 있습니다. 사이트 어댑터가 공식 직군 등으로 후보를 추리고, `CategoryClassifier`가 회사 × 분야별 제목과 본문 근거를 확인합니다. 공통 실행부에는 개발 직군 키워드 필터가 없습니다. 신규 사이트 구현과 기존 크롤러 변경의 절차·문서 갱신 기준은 [크롤러 어댑터 표준](../docs/crawler-adapter-standard.md)을 따릅니다.

- 네이버: 통합·클라우드·SNOW/KREAM·랩스·웹툰·파이낸셜·I&S·Z.
- 토스: 통합 포털의 실제 계열사별 개별 포지션.
- 카카오: 본사, 페이·뱅크·페이증권·모빌리티·엔터프라이즈·엔터테인먼트·스타일·VX·페이손해보험·디케이테크인·키즈노트 별도 포털.
- 쿠팡: 그룹 공식 Greenhouse 전체 보드. 상세 본문은 공개 Greenhouse API에서 먼저 확인합니다. 기존 버전과 본문이 같으면 저장된 캡처를 재사용하고 `www.coupang.jobs` 원문 페이지를 열지 않습니다. 신규·변경 공고만 일반 Chromium 모드와 컨테이너 내부 Xvfb 가상 화면에서 원문을 열어 제목·본문이 API 내용과 맞는지 확인한 뒤 캡처합니다. API 또는 원문 페이지의 보안 확인·접근 제한은 실패로 남기며, 해당 화면은 공고로 저장하지 않습니다.
- 당근: 당근·당근페이 통합, 당근서비스, 북미·일본 포털.
- LINE: LINE Careers 전 지역, LY 일본, LY Communications.
- 우형: 우아한형제들·우아한청년들.
- 오늘의집: 버킷플레이스 공식 채용 목록과 Greeting 상세. 개발뿐 아니라 기획·PM·디자인·마케팅·영업을 지원합니다. [수집 규칙](../docs/source-bucketplace.md).
- 새 사이트 9곳: 쏘카(공식 목록+Greeting 상세), 컬리·무신사·직방·여기어때(Greeting), 야놀자(Workday), 리디(RoundHR), 채널톡(자체 공개 페이지), 뱅크샐러드(공식 목록+Greeting 상세). 구현 범위와 사이트별 실제 확인은 [직무 지원표](../docs/job-categories.md) 및 각 `source-*.md` 문서를 따릅니다.

이 목록은 구현된 공개 포털 범위이며 모든 법인의 존재/채용 여부를 보증하는 법인 목록은 아닙니다. 최신 등록 범위는 `/api/sources`의 `portals`에서 확인합니다. 공고가 0건인 포털도 정상 수집 결과이며, 구조 변경은 실패로 기록합니다.

실행 버튼 한 번에 기록 하나를 만들고, 각 계열사 포털의 목록 조회는 독립 작업으로 저장합니다. 한 포털이 실패해도 다른 포털과 공고 처리는 계속하며, 목록 재시도는 실패한 포털만 다시 실행합니다. 공고는 공통 MySQL 큐에 넣고 빈 작업자가 가져갑니다.

공고 작업자는 기본 3개(`CRAWLER_WORKERS`), 지원 페이지 작업자는 기본 2개(`CRAWLER_APPLICATION_PAGE_WORKERS`), 사이트별 활성 페이지 작업은 기본 1개(`CRAWLER_SITE_CONCURRENCY`)입니다. 별도 목록 탐색 스레드 1개도 사이트 제한을 공유합니다. 이는 페이지 작업 제한이며 이미지·CSS 등 모든 하위 리소스 요청의 개수 제한은 아닙니다. `site-overrides`로 호스트별 동시성, `minimum-navigation-interval-overrides`로 호스트별 요청 시작 간격을 설정합니다. 현재 기본값은 `www.coupang.jobs` 5초, `boards-api.greenhouse.io` 3초이며, 이는 429가 발생하지 않는다고 검증된 한계값이 아니라 요청을 분산하기 위한 보수적 시작값입니다. 쿠팡의 상세 API 응답도 호스트 제한 대상입니다.

각 작업자는 자기 스레드에서만 Playwright와 브라우저를 생성·재사용·정리합니다. 작업별 컨텍스트는 분리합니다. 쿠팡 상세만 일반 브라우저 모드를 사용하며, 다른 모드의 작업으로 전환할 때 기존 브라우저를 정리해 작업자당 브라우저 하나를 유지합니다. Java는 컨테이너 PID 1로 실행해 종료 신호를 직접 받습니다. 임대 토큰과 주기적 갱신으로 중복 처리를 방지하고, 중단된 공고 작업은 임대 만료 후 체크포인트부터 다시 처리합니다. 브라우저 탐색·본문·캡처는 DB 트랜잭션 밖에서 수행합니다.

목록·진행률 조회에는 본문을 포함하지 않습니다. `/api/postings/{id}`에서 본문을 읽고, 캡처는 `/api/captures/{해시}.png`에서 선택적으로 불러옵니다. 변경된 공고만 새 버전을 저장하고 이전 버전은 보관합니다. 저장된 진행 중 공고도 다음 수집에 다시 확인하며, 명시적인 마감만 보관 처리합니다.

설정은 `src/main/resources/application.yaml`을 참고하세요. DB 스키마는 Flyway가 관리하며 JPA는 `validate`만 수행합니다. DB Docker 볼륨과 `backend/storage/captures`는 일반 종료 시 삭제하지 않습니다.


### 분야별 수집

`/api/state`의 `categories`와 `companies`에서 지원 분야·계열사 조합을 확인합니다. `all`은 각 사이트 어댑터가 지원하는 전체 공고이며 별도 공고 태그로 저장하지 않습니다. 개발 분야는 웹 백엔드·프론트엔드·Android·iOS·풀스택·데이터 엔지니어·ML 엔지니어·DevOps/SRE입니다. 비개발 5개 분야는 토스 9개 계열사·당근·당근페이·오늘의집에서만 지원합니다. [회사별 규칙](../docs/non-development-sources.md). 분야별 후보 탐색은 회사별 전략을 유지하고, 최종 본문 판정과 저장 직군은 일반 규칙 v0.5를 적용합니다. 전체 78개 회사 × 분야 지원표와 제외 기준은 [분야 문서](../docs/job-categories.md), 기존 어댑터의 선행 필터는 [네이버·토스·카카오](../docs/adapter-role-coverage.md), [쿠팡·당근·LINE·우형](../docs/adapter-role-coverage-extra.md)을 참고하세요.

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
- `GET /api/state`, `/api/postings`, `/api/postings/{id}`: 각 공고의 `categories`(복수 가능), `classification`, `firstSeen`, `lastSeenAt`, `closed` 제공. `lastSeenAt`은 마지막 성공 관측 시각이며 마감 공고 상세에서는 마감 확인 관측 시각으로 표시한다. 실제 접수 종료 시각을 뜻하지 않는다. 아직 분류하지 않은 과거 데이터는 `classification: null`.
- 공고별 비관적 잠금과 짧은 트랜잭션으로 최신 본문을 읽고 연결을 교체합니다. 수집·읽음 변경과 같은 공고 잠금을 사용하며, 개별 실패는 목록으로 반환합니다. 재분류는 브라우저를 만들지 않습니다.

실제 Kotlin 결과 평가 도구: `java -cp 'backend/build/classes/kotlin/main:backend/build/resources/main:backend/build/runtime-libs/*' scripts/analysis/EvaluateKotlinClassifier.java /path/corpus.jsonl /tmp/predictions.json` (루트에서 빌드 후 JDK 21 이상). 고정 정답과 Python 해석기 대조 결과는 [구현 검증](../docs/analysis/classification-implementation-v0.5.md)에 기록합니다.

## 관심 공고 API

`interest` 영역은 도메인 `PostingInterest`와 저장 포트, Spring Data JPA 구현으로 구성합니다. Flyway V8의 `posting_interest`에 공고 ID당 한 행을 두고 수집 결과/본문 버전과 분리합니다. 목록 조회는 관심 정보를 한 번에 읽어 N+1을 피합니다. `/api/state`, `/api/postings`, `/api/postings/{id}`의 `interest`에는 `starred`, `note`, `priority`, 레거시 `applicationStatus`·`appliedOn`, `updatedAt`을 제공합니다. 실제 지원 상태의 기준은 V18 `application_case`이며 레거시 상태 필드는 호환·이관용으로만 유지합니다.

| 요청 | 본문 | 동작 |
| --- | --- | --- |
| `PATCH /api/postings/{id}/interest` | `{"starred":true}` | 별표만 변경. 메모·지원 기록 유지 |
| `PUT /api/postings/{id}/interest/management` | `{"note":"확인할 내용","priority":"HIGH"}` | 관심 메모·우선순위 저장. 별표와 지원 상태 유지 |

두 쓰기 API는 갱신한 `interest` 객체를 반환합니다. 우선순위는 `HIGH/NORMAL/LOW/NONE`, 메모는 최대 5,000자입니다. 지원 상태·지원일은 이 API에서 변경하지 않습니다. 공고당 지원 상태는 `/api/applications`의 별도 지원 건에서 관리합니다.

공고의 비관적 잠금을 잡고 기존 관리 정보를 읽어 수정하므로 최초 행 생성·별표·메모·읽음·수집 저장이 공고 단위로 직렬화됩니다. 별표 해제에 행 삭제를 사용하지 않습니다. 수집기와 저장 본문 재분류는 `posting_interest`를 갱신하지 않습니다. 마이그레이션은 기존 공고/캡처/기록을 삭제하지 않습니다.

2026-11-16 검증: Flyway V8 적용과 실제 DB/API에서 등록·해제·재등록 보존·지원 전 복구·동시 최초 저장·입력 오류(400/404)·기존 공고/버전/분류/기록 보존을 확인했습니다. 임시 관리 정보는 검증 후 원복했습니다. 실제 재수집을 통한 검증은 실행하지 않았습니다. [검증 범위](../docs/interest-postings-spec.md).

## 개인 데이터 관리·프리셋·변화 요약 API

설계와 삭제 범위는 [`docs/workspace-tools-spec.md`](../docs/workspace-tools-spec.md)를 따른다. V9는 프리셋·실행별 변화·작업공간 리비전을, V10은 한 실행의 다중 직군 범위를, V12는 경력 자료와 AI 정리 후보를, V13은 공고별 지원서 요구사항 이력을, V14는 경력 추천·맞춤 초안 작업을 추가한다. 일반 CRUD는 Spring Data JPA를 사용하며, 전체 백업/복원과 일괄 초기화만 고정 테이블 목록의 JDBC 배치와 트랜잭션을 사용한다. FK를 끄거나 TRUNCATE하지 않는다.

| API | 용도 |
| --- | --- |
| `GET /api/collection-presets` | 저장된 선택 목록 |
| `POST /api/collection-presets` | `{name, categoryIds, companyIds} (단일 categoryId도 허용)` 저장 |
| `PUT /api/collection-presets/{id}` | 선택과 이름 변경 |
| `DELETE /api/collection-presets/{id}` | 프리셋 삭제 |
| `POST /api/collection-presets/{id}/start` | 저장된 조합으로 수집 실행 |
| `GET /api/collections/{id}/changes` | 신규·변경·마감 및 당시 관심 공고 변화의 ID 목록, 과거 기록 여부 |
| `GET /api/workspace` | 데이터 건수·캡처 및 경력 원본 용량 |
| `POST /api/workspace/reset` | `{scope: interests\|postings\|read\|presets\|career, confirmation: "초기화"}` |
| `POST /api/workspace/captures/cleanup` | `{confirmation: "정리"}`로 미사용 캡처와 경력 원본 삭제 |
| `GET /api/workspace/backup` | ZIP 다운로드, 수집 중 409 |
| `POST /api/workspace/backups/preview` | multipart `file` 업로드, 검증 후 토큰·건수 반환 |
| `POST /api/workspace/backups/{token}/restore` | `{confirmation: "복원"}`, 현재 전체 데이터 교체 |
| `DELETE /api/workspace/backups/{token}` | 미리보기 취소 및 임시 파일 삭제 |

## 공고별 지원서 원문

V13 `posting_application_requirement`는 공고에 종속된 원문 이력 테이블이며 공고 삭제 시 cascade로 함께 삭제된다. V15는 수집 실행 연결과 실제 지원 페이지 원문 확인 여부를 추가하고, V16은 상태를 원문 저장·페이지 확인·실패로 단순화한다. V17은 실행별 확인 이력과 실제 원문 버전을 구분하고, 같은 원문이면 기존 버전 ID만 참조하도록 한다. 지원서 원문 전용 데이터에는 AI 결과를 저장하지 않으며, 사용자가 별도 요청하는 지원 준비 분석은 V18 작업 테이블에 저장한다. 이전 원문 텍스트는 유지하고, 이전 AI 결과는 지우며, `POSTING_BODY` 출처는 빈 원문과 사용 불가 사유를 남긴다. 실행 이력이 삭제되면 실행 연결만 `NULL`로 바꾸고 원문 이력은 보존한다.

| 요청 | 동작 |
| --- | --- |
| `GET /api/postings/{postingId}/application-requirements` | 공고별 지원서 확인 이력과 원문 버전 정보를 최신순 조회. `versionSaved`는 실제 원문 버전 여부, `contentVersionId`는 이력이 가리키는 원문 버전 |
| `POST /api/postings/{postingId}/application-requirements` | `{source: USER_PASTED, sourceUrl, rawText}`를 저장. 최신 원문과 같으면 기존 버전을 반환하고 행을 추가하지 않음 |

공개 지원 페이지 확인 API나 별도 재확인 버튼은 제공하지 않는다. `TaskService.finish()`가 열린 공고를 저장할 때 현재 수집 실행 ID로 지원 페이지 확인 작업을 예약한다. `(collection_run_id, posting_id)` 고유 인덱스로 실행 안에서 중복을 막는다. 실패한 페이지는 다음 공고 수집 실행에서 확인한다.

`/api/state` 회사 항목의 `applicationPageStatus`는 `unchecked`, `checking`, `public`, `mixed`, `login-required`, `failed`, `review` 중 하나다. 한 공고의 실패만으로 회사 전체 미지원이라고 단정하지 않는다. 실행의 `applicationProgress`와 `applicationResults`는 공고 수집과 독립된 페이지 진행률과 회사별 결과를 제공한다.

지원서 원문은 최대 100,000자다. 자동 확인은 공고 페이지에서 공개 지원 링크를 찾고 최대 12개까지 시도한다. 링크가 없으면 폼 바깥의 비제출 `지원하기` 버튼만 눌러 진입을 시도한다. `type=submit`, 폼 안 버튼, 로그인·동의·입력·업로드·제출은 하지 않는다. 실제 지원 페이지의 보이는 텍스트만 저장하고 원래 공고 URL의 본문은 대체재로 쓰지 않는다. 새 컨텍스트에는 쿠키·기존 로그인 세션이 없고 HTTPS 공개 IP만 허용하며 redirect·하위 요청의 사설/로컬 주소를 막는다. 로그인 화면은 로그인 필요 사유와 함께 실패로 표시한다.

전용 `applicationPageWorkers` 풀은 `CRAWLER_APPLICATION_PAGE_WORKERS`(기본 2)로 설정하며, 각 스레드는 자체 Playwright·브라우저를 생성·재사용한다. 공고 수집 작업자와 병렬로 동작하고 같은 `SiteLimiter`를 공유한다. 수집 실행은 매번 범위에 포함된 열린 공고 페이지를 다시 확인한다. `version_saved`는 실제 원문 버전만 표시하고, 확인 이력의 `content_version_id`는 연결된 버전을 가리킨다. 최신 저장 원문과 본문이 같으면 확인 이력은 `SAVED`로 남기되 `raw_text`를 복사 저장하지 않고 기존 버전을 참조한다. 새 원문만 본문과 함께 버전으로 저장한다. 사용자 저장도 최신 원문과 같으면 새 행을 만들지 않는다. 공고 행 잠금과 최신 버전 잠금 조회를 사용해 동시 작업에서도 중복 버전 생성을 막는다. 공고 본문·캡처와 마찬가지로 기존 버전은 덮어쓰지 않는다. 원문 저장만으로 Codex에 보내지 않으며, 문항 추출 요청에서만 지원 준비 작업 입력으로 전달한다.

## 공고별 지원 준비

V18은 `application_case`, `application_question`, `application_material_draft`, `application_interview_session`, `application_interview_turn`, `application_ai_task`를 추가한다. 공고당 지원 건은 최대 하나이며 `posting_id` 고유 인덱스로 강제한다. 이전 `posting_interest.application_status='APPLIED'` 행은 마이그레이션 중 지원 완료 지원 건으로 옮기고 기존 날짜를 유지한다. `BEFORE_APPLY` 행은 미시작 상태로 남긴다. 관심·메모·우선순위와 지원 건은 별개이며 관심 초기화는 지원 건을 지우지 않는다.

| 요청 | 동작 |
| --- | --- |
| `GET /api/applications` | 최근 변경 순 지원 건과 공고 요약 |
| `GET /api/applications/statuses` | 관심 목록 배지용 공고별 상태·지원일 조회 |
| `POST /api/postings/{postingId}/application-case` | 공고 지원 건을 생성하거나 기존 건 반환 |
| `GET /api/applications/{applicationId}` | 지원 건·원문·문항·자료·최근 작업·면접 세션 조회 |
| `PATCH /api/applications/{applicationId}/status` | `PREPARING`·`APPLIED` 상태 변경. `APPLIED` 최초 전환 시각은 서버가 기록 |
| `/api/applications/{applicationId}/questions/**` | 원문에서 문항 추출 요청, 수동 CRUD, 문항별 답변 생성·저장 |
| `/api/applications/{applicationId}/materials/**` | 맞춤 이력서·포트폴리오 생성 및 수정·저장 |
| `/api/applications/{applicationId}/interview/**` | 공개 면접 조사, 질문 기반 경험 인터뷰·꼬리 질문·승인된 경력 후보 저장 |
| `/api/applications/{applicationId}/tasks/{taskId}` | 저장된 작업 진행·결과 조회 및 실패 작업 재시도 |
| `/api/applications/worker/tasks/**` | 지원 준비 큐의 claim·lease·진행 상태·완료·실패 보고 |

AI 생성 요청은 DB 작업을 만들고 `202 Accepted`와 작업 ID를 즉시 반환한다. 작업 유형은 지원서 문항 추출, 문항별 답변, 맞춤 자료, 면접 조사, 경험 인터뷰 꼬리 질문이다. 작업은 `WAITING_AI`·`ANALYZING`·`COMPLETED`·`FAILED` 상태로 보관하며 워커가 없어도 대기한다. `AiWorkerMain`은 지원 준비 큐를 AI 질문·경력 자료·추천/초안 큐와 순환해 claim한다. UI는 작업 상태를 조회하고 재진입 때 최근 작업에서 대기 상태를 복원한다.

맞춤 자료·문항 답변·면접 조사·경험 인터뷰는 `REJECTED`가 아닌 경력 사실 중 공고/문항 관련도가 높은 항목을 먼저 선별해 최대 80개 전달한다. 원문 인용 ID가 입력 사실 집합에 있으면 evidence로 연결하고, ID가 없거나 무효여도 초안 문안은 저장하며 UI가 원문 대조 필요를 표시한다. 문항 답변은 글자 수 제한을 넘으면 저장하지 않는다. 공개 면접 조사는 `gpt-6-luna`와 live web search를 사용하며 공식 사실·복수 후기에서 반복된 경향·개별 후기·공고 추론을 구조화한다. URL은 HTTP(S) 공개 주소만 허용하고, 반복 경향에는 서로 다른 후기 URL 2개 이상을 요구한다. 지원서 원문·공고·사용자 경력 파일 안의 지시문은 분석 데이터로 취급한다. Codex는 지원 상태 변경이나 실제 지원 제출을 하지 않는다.

2026-11-22 메인 데이터 검증: 수집과 지원 페이지 확인 큐가 모두 종료된 뒤 Docker 앱을 재빌드·재시작했다. 이전에 `근거가 연결된 이력서 초안을 만들지 못했습니다`로 실패했던 토스 서버 개발자 지원 자료 작업을 `gpt-6-luna`로 재시도해 완료했고, 이력서 제목·요약·섹션 1개와 포트폴리오 프로젝트 2개가 초안 버전 1로 저장되는 것을 확인했다. 이번 결과는 근거 ID가 모두 비어 있었지만 본문이 보존됐고 `hasUnlinkedContent=true`와 원문 대조 안내가 저장됐다. 전달된 경력 자료에 테스트용 가상 정보 안내가 있어 AI가 실제 수행 여부·기술·역할 확인을 요청했고, 수치 성과를 임의로 넣지 않았다. 로컬 AI 워커 PID와 `/api/ai/status`의 새 `workerId` heartbeat 일치, 기존 DB/캡처 볼륨 보존도 확인했다. 당시 해당 지원 건에 문항이 없어 미작성 답변 전체 생성은 UI 빌드만 확인했고 AI 요청은 하지 않았다. 80개 초과 경력 사실의 선별은 이번 실제 데이터에서 발생하지 않았다.

지원 건은 ZIP 백업에 포함한다. `interests` 초기화는 관심 데이터만 삭제하고, `postings`는 지원 건과 종속 기록을 함께 삭제한다. `career` 초기화는 지원 상태·문항은 남기고 답변·맞춤 자료·면접 세션/기록 및 지원 AI 작업을 지운다.

2026-11-22 격리 검증: `./gradlew compileKotlin`, `./backend/gradlew classes copyRuntimeLibs`와 스키마 V18 Docker 기동을 확인했다. 이전 지원 완료 날짜 `2025-03-14` 이관, 공고당 지원 건 멱등 생성, 상태 변경, 질문·답변 CRUD, 관심 초기화 후 지원 기록 보존, 경력 초기화 후 지원 건·문항 보존을 임시 DB에서 확인했다. ZIP 백업 스키마 18에 지원 테이블·작업·경력 인터뷰 근거가 포함됐다. 가상 경력 근거 하나로 별도 워커 `PID 55502 / workerId jobmoa-appworkspace-verification`의 heartbeat와 `gpt-6-luna` 모델을 확인하고, 맞춤 이력서·포트폴리오와 문항 답변을 실제 생성했다. 결과 fact ID·근거 인용과 수정 저장 후 evidence 보존을 확인했다. 별도 가상 `Toss / Backend Engineer` 건의 면접 검색도 실행해 `searchPerformed=true`, 공식 자료 3건, 서로 다른 후기 URL 3개를 근거로 한 반복 경향 1건, 개별 후기 1건, 공고 추론 3건, 질문 4개를 저장했다. 경험 인터뷰 꼬리 질문을 생성하고 승인 API로 저장한 자료가 `UNVERIFIED`이며 답변 인용 evidence를 보존하는 것도 확인했다. 첫 확인에서 인터뷰 승인 자료의 evidence가 빠지는 결함을 발견해 저장 경로를 고친 뒤 다시 확인했다. 격리 워커는 작업별로 종료했고 기존 메인 워커·DB·운영 공고·수집은 건드리지 않았다.

2026-11-22 지원 준비 화면 격리 검증: Node.js 22 `npm run build`와 백엔드 `./gradlew compileKotlin`이 통과했고, 별도 Docker 스택에서 지원 자료 편집·문항 탭·지원 상태 변경과 관심 목록의 읽기 전용 배지를 브라우저로 확인했다. 합성 공고·경력 데이터만 사용했다. 공개 면접 웹 검색·AI 생성 검증은 위 백엔드 기록에서 별도로 수행했다. 운영 DB·메인 워커는 건드리지 않았고 실제 공고 수집이나 지원서 제출은 하지 않았다.

2026-11-22 메인 반영·실제 수집 확인: `./start.sh`로 Docker를 재빌드했고 MySQL·백엔드·프론트가 healthy, Flyway가 V16 적용을 완료했다. 데이터 초기화 없이 실행했다. 브라우저의 전체 기업·전체 직군 수집으로 78개 회사, 451개 공고 작업을 시작했다. 12:23 KST 관측 시 공고 작업 289 완료·1 실패·1 처리 중·160 대기(64%), 지원 페이지 작업 281개 중 23 저장·19 실패·2 처리 중·237 대기였다. 뱅크샐러드 `[Frontier] AI Native Server Engineer`(공고 1205)는 이번 실행 지원 페이지 원문 329 Unicode 문자 저장을 API에서 확인했고 프론트 한 상자에 최신 `원문 저장됨`으로 표시되는 것을 확인했다. 전체 수집은 관측 시점에 계속 실행 중이며 다른 지원 페이지 내용은 열어보지 않았다.

2026-11-22 원문 버전 중복 방지 반영 확인: 백엔드 `./gradlew compileKotlin`, Node 22 프론트 `npm run build` 및 `./start.sh` Docker 재빌드를 통과했다. Flyway가 V17까지 적용됐고 MySQL·백엔드·프론트가 모두 healthy였다. 데이터 초기화나 새 수집 실행은 하지 않았다. 기존 공고 1205의 지원서 원문 API에서 `versionSaved=true`, `contentVersionId`가 자기 ID를 가리키고 원문 331자를 유지하는 것을 확인해 기존 저장 자료가 보존됨을 확인했다. 기존 실행 `639a6a81-b170-4760-a0a1-1dae7c604346`의 공고 작업은 450/451 완료·1 실패였고, 12:40 KST 관측 시 지원 페이지 확인은 442건 중 175 저장·152 실패·2 처리 중·113 대기였다. 이미 해당 실행에 등록되어 있던 지원 페이지 작업은 배포 후에도 DB 큐에서 계속 진행했다.

12:50 KST 최종 확인에서 지원 페이지 작업은 442건 중 290 저장·152 실패로 종료됐다. 해당 실행의 저장 자료를 읽기 전용 SQL로 집계한 결과, 저장 290건 중 71건은 `version_saved=0`으로 기존 버전을 참조하는 동일 원문 확인 이력이고 219건은 새 원문 버전이었다. 동일 확인 이력의 참조 대상은 모두 같은 공고의 실제 버전이었다(잘못된 참조 0건). 이는 이미 진행 중이던 큐를 관찰한 결과이며 새 수집이나 실제 지원 제출은 하지 않았다.

## 경력 자료와 Codex 분석

V12가 원본 문서·추출 구간·AI 경력 정보·근거 인용 테이블을 만든다. 원본은 `${STORAGE_DIRECTORY}/career-materials/{uuid}.bin`에 두고, 문서 내용은 Apache Tika 표준 파서로 감지·추출한다. 페이지 구분과 제목/문단 구간을 저장하며, OCR은 Tesseract 한국어·영어 사전을 사용한다. 파서가 처리하지 못하거나 본문이 비어 있어도 원본과 실패 이유는 남긴다. 업로드는 파일당 50 MiB, 한 번에 20개·총 200 MiB까지 받는다. 추출 본문 저장은 파일당 400만 자, Codex 전달은 파일당 16만 자까지 제한한다. 확장자 검증은 하지 않고 SHA-256 내용 해시로 동일 파일 중복을 막는다. V14는 사용자가 제외하지 않은 경력 정보를 바탕으로 추천·공고별 맞춤 이력서·포트폴리오 초안을 `career_assistant_task`에 저장한다.

| API | 용도 |
| --- | --- |
| `GET /api/career-materials` | 등록 파일·형식·크기·처리 상태 목록 |
| `POST /api/career-materials` | multipart `files` 여러 개 등록·본문 추출·비동기 분석 큐 등록 |
| `GET /api/career-materials/{id}` | 본문 구간과 AI 사실 후보·근거 인용 조회 |
| `PATCH /api/career-materials/{id}/facts/{factId}` | 제목·설명과 상태 수정. `UNVERIFIED`·기존 `VERIFIED`는 추천에 포함하고, `REJECTED`는 제외 |
| `GET /api/career-materials/{id}/original` | 원본 파일 다운로드 |
| `DELETE /api/career-materials/{id}` | 원본·추출 본문·후보를 함께 삭제 |
| `POST /api/career-materials/{id}/retry` | AI 분석 실패 파일을 다시 대기열에 등록 |
| `GET /api/career-materials/worker/status` | 선택 Codex 워커 상태 |
| `/api/career-materials/worker/tasks/**` | 작업 claim·lease 갱신·완료·실패 보고 |
| `GET /api/career-assistant/readiness` | 추천에 포함되는 경력 정보·열린 공고 건수 |
| `POST /api/career-assistant/recommendations` | `{count, preferenceText, categoryIds, companyIds, locationKeywords}`로 추천 작업을 등록하고 `202`와 작업 ID 반환. `preferenceText`는 최대 1,200자, 지역은 최대 200자이며 나머지 조건은 선택 사항 |
| `GET /api/career-assistant/recommendations` | 최근 추천 작업 및 저장된 결과 |
| `POST /api/career-assistant/recommendations/{id}/drafts` | 추천 결과에서 선택한 공고의 맞춤 초안 작업 등록 |
| `GET /api/career-assistant/recommendations/{id}/drafts` | 선택한 추천 결과의 초안 작업·결과 조회 |
| `GET /api/career-assistant/tasks/{id}` · `POST .../{id}/retry` | 개별 작업 상태·실패 재시도 |
| `/api/career-assistant/worker/tasks/**` | 추천·초안 작업 claim·lease 갱신·완료·실패 보고 |

워커는 AI 질문·경력 자료·경력 추천/초안·공고별 지원 준비 네 큐를 순환 조회해 한 종류가 계속 앞서지 않게 한다. 지원서 원문은 저장만으로 AI에 보내지 않고, 문항 추출 요청에만 작업 입력에 포함한다. 전역 AI 질문과 공고별 면접 조사는 웹 검색을 허용하고, 경력 자료·추천·초안·문항 추출/답변은 요청 자료만 사용한다. `gpt-6-luna`에서 생성한 경력 후보의 각 인용은 API가 추출 구간에 실제 포함되는지 재검증한다. 추천·초안은 `REJECTED`가 아닌 모든 경력 정보를 사용하며 `UNVERIFIED` 상태는 사용자의 확정을 기다리지 않는다. `VERIFIED`는 기존 흐름에서 확인된 항목을 보존하는 상태이고, 추천 포함 여부와는 무관하다. AI 추출 요약은 원문 인용과 대조하고 근거 밖의 수치·역할·성과를 만들지 않도록 prompt에 명시한다. 추천 공고 ID와 사실 ID는 요청 후보와 대조한다. 추천의 직군·회사는 각각 ID 선택 조건으로, 지역은 쉼표 구분 검색어로 서버에서 후보에 적용한다. 각 항목 안에서는 선택값 중 하나라도 일치하면 포함하고, 여러 항목은 모두 만족해야 한다. 선택 조건은 모두 비워둘 수 있다. 자연어 `preferenceText`는 하드 필터가 아니라 후보 순위와 Codex 판단에 쓰는 희망 조건이며, 명시적인 제외 요청만 추천에서 제외한다. 서버는 조건 적용 후 열린 공고의 최신 본문과 경력 정보·희망 조건의 키워드 연관도로 최대 36개를 골라 Codex에 전달하고, 요청한 최대 20개까지만 추천한다. 맞춤 초안은 선택한 공고 본문 최대 18,000자와 제외되지 않은 경력 정보 최대 80개를 전달하고 각 항목에 근거 사실 ID가 없는 경우 결과에서 제외한다. 워커가 꺼져 있어도 추천·초안 작업은 DB에 저장되어 대기한다. 경력 파일명·추출 텍스트, 추천에 포함된 경력 정보와 선택 공고 본문은 각 요청 때 Codex 서비스로 전송된다.

백업·복원은 경력 원본·추출 자료·추천 제외 상태·추천·맞춤 초안 작업·공고별 지원 준비 건을 함께 포함한다. `career` 초기화는 경력 기반 추천·초안과 지원 답변·맞춤 자료·면접 기록을 비우되 지원 건·상태·문항은 유지한다. `postings` 초기화는 공고별 지원서 정보·지원 건·문항·답변·맞춤 자료·면접 기록·추천/초안도 지우되 경력 자료와 경력 정보는 유지한다.

2026-11-22 경력 정보 자동 반영 검증: `./gradlew compileKotlin`과 메인 `./start.sh`의 백엔드·프론트 이미지 빌드가 통과했고 세 Docker 컨테이너가 healthy로 올라왔다. `GET /api/career-assistant/readiness` 응답의 `availableFactCount`는 `UNVERIFIED`와 기존 `VERIFIED`를 포함하고, `REJECTED`는 제외하는 조회 결과로 확인했다. 화면에서도 준비도와 추천 버튼이 표시됐다. 추천·초안 POST는 실행하지 않았다. Codex 워커는 이전 프로세스와 추적 파일을 정리하고 새 PID 및 실행별 `workerId` heartbeat로 재시작을 확인했다. 다만 시작 직후 기존 대기 AI 작업은 Codex 사용량 한도 오류를 기록해 AI 응답 생성까지는 검증하지 않았다.

2026-11-22 추천 조건 적용 검증: `./gradlew compileKotlin`과 메인 `./start.sh`의 백엔드·프론트 Docker 빌드가 통과했고 MySQL·백엔드·프론트 컨테이너가 모두 healthy, 백엔드 readiness가 `UP`이었다. `./gradlew test`는 `NO-SOURCE`로 끝나 백엔드 자동 테스트 소스가 없는 상태다. 재시작 후 DB·캡처 볼륨이 유지됐다. 추천 POST·Codex 분석·개인 파일 업로드는 실행하지 않았으며, 브라우저 입력 확인 범위는 [프론트 검증 기록](../frontend/README.md#경력-자료)에 적었다.

백업 형식은 `jobmoa` v2 / DB 스냅샷 스키마 18이며, 복원은 스키마 10~18과 형식 v1·v2를 지원한다. 스키마 14는 지원 페이지 확인을 수집 실행과 연결하고 실제 페이지 원문 저장 여부를 보관한다. 스키마 15는 지원서 AI 결과·확정 상태를 제거하고 원문 상태를 저장·확인 대기·실패로 단순화한다. 스키마 16은 실제 원문 버전 여부와 확인 이력의 버전 참조를 추가한다. 스키마 18은 공고별 지원 건·문항·답변·맞춤 자료·면접 조사·경험 인터뷰를 포함한다. 스키마 10~17 백업에는 지원 준비 자료가 없으므로 미리보기에서 알린다. 스키마 13~15 백업의 이전 열은 복원 시 버전 여부·참조를 추론해 보완한다. ZIP은 `workspace.json`, `captures/{sha256}.png`, `career-materials/{uuid}.bin`으로 구성된다. 복원 미리보기는 백업에서 빠져 전체 교체로 삭제될 현재 데이터를 알린다. 백업 생성 시 진행 중인 AI 작업은 다시 대기하도록 정리한다. ZIP 2 GiB·해제 4 GiB·개별 경력 원본 50 MiB·캡처/JSON 128 MiB 제한이다. 임시 파일은 `${STORAGE_DIRECTORY}/workspace-tmp`에 저장하고 만료·재시작 잔여물을 정리한다. Spring multipart와 Docker 프론트 NGINX의 업로드 크기/타임아웃을 함께 조정했다. 백업 생성은 파일 스트리밍을 사용하지만 스냅샷 JSON·DB 행은 메모리에 보관하므로 한도까지의 대규모 복원 성능은 별도 검증이 필요하다.

`/api/state`의 `workspaceRevision`은 초기화·복원 때 바뀐다. 클라이언트는 리비전이 바뀌면 이전 버전 ID의 본문 캐시를 버린다. 유지보수는 수집 중 409로 차단하며, 사용자 확인 문구도 서버에서 검증한다.

외부 접속 없는 격리 검증 또는 보관 데이터만 읽는 실행은 `--jobmoa.crawler.enabled=false`로 크롤러 런타임을 제외할 수 있다. 기본값은 true다. 이 옵션은 수집 API를 비활성화하지 않으므로 실행을 만들면 런타임을 다시 켤 때까지 대기한다.

`POST /api/collections`는 선택적인 `categoryIds: string[]`도 받는다. 하나의 실행에 직군 합집합을 저장하며 후보 URL과 본문 확인 모두 실행의 범위를 사용한다. `categoryId`는 기존 단일 직군 클라이언트와의 호환용 대표 값이다. 재시도에도 전체 범위를 보존한다.

### 2026-11-16 격리 검증

운영 DB와 분리한 임시 MySQL DB·저장 디렉터리에서 3개 공고/6개 버전/공유 캡처 1개의 픽스처로 확인했다. 현재 사용자 데이터 건수를 뜻하지 않는다.

- 프리셋 생성·수정·삭제·조합 검증, 다중 직군 실행과 공통 큐의 URL 중복 방지. 크롤러 런타임을 끄고 확인하여 외부 사이트 요청은 하지 않았다.
- 관심·읽음·공고·프리셋 초기화와 확인 문구 검증, 수집 중 초기화·백업·복원 409 차단.
- 백업 → 초기화 → 복원 후 공고·버전·개인 관리 정보·실행 기록 동일성, 캡처 복구, 리비전 변경.
- 경로 이탈 ZIP, 손상/누락 캡처, 다른 스키마, 중복 기본 키, 끊어진 참조 거부. 복원 INSERT 제약 위반 시 전체 삭제·삽입·리비전 롤백과 기존 데이터 유지.
- 실제 `TaskService.finish`를 임시 DB에서 호출해 본문 동일 + 마감 전환 시 버전 미증가, 본문 변경 시 새 버전 및 관심 변화 기록, 이후 관심 해제에도 과거 스냅샷 유지.

정식 테스트 소스는 추가하지 않았으며 빌드와 일회성 API·서비스 프로브로 검증했다. 대용량 상한까지의 부하 검증과 외부 사이트 재수집은 수행하지 않았다.

백업을 생성할 때도 복원과 같은 JSON·개별 캡처·전체 해제 크기·파일 수 제한을 검사해 자체 복원이 불가능한 ZIP을 만들지 않는다. 업로드 HTTP 본문은 multipart 경계 정보를 고려해 2049 MiB로 허용하되 실제 ZIP 파일은 서비스에서 2 GiB 이하로 검증한다.

### 2026-11-17 실제 저장 데이터의 읽기 전용 검증

Docker 반영 전후 공고 메타데이터·버전 목록·읽음 상태·개인 관리 정보가 동일하고 수집 실행 수가 유지되는 것을 확인했다. 확인 당시 437개 공고·437개 버전·436개 캡처를 NGINX의 `/api/workspace/backup`으로 내려받아 약 312 MB ZIP을 생성했다. 이 건수는 검증 시점의 스냅샷이며 현재 데이터 수를 안내하는 값이 아니다. ZIP을 별도 임시 서버의 미리보기에 업로드해 전체 데이터 구조·참조와 캡처 SHA-256을 검증한 뒤 폐기했다. 실제 사용자 DB에는 초기화·복원·재수집을 실행하지 않았다.

## 본문이 사라진 공고의 사이트별 마감 판별 (2026-11-21)

구현 기준과 실제 화면 확인 범위는 [사이트별 공고 마감 판별](../docs/closure-detection.md)을 참고한다. `RecruitmentSite.closureReason()`은 본문 추출 전에 사이트별 종료 증거를 확인하고, `PostingClosed`는 `TaskService.closeExisting()`으로 전달된다. 기존 공고의 상태·관측·관심 변화만 갱신하고 정상 버전·캡처·개인 관리 정보는 보존한다. 미저장 공고의 종료 화면은 저장하지 않는다.

`BrowserPage.navigatePosting()`만 404/410의 렌더링된 안내를 확인할 수 있다. 일반 목록 탐색, 403/429/5xx의 실패 정책은 유지한다. HTTP 404 하나나 목록 누락은 마감 근거가 아니다. 브라우저 응답 상태·현재 URL·네이티브 알림과 사이트의 DOM/SSR을 함께 확인한다. 공식 JSON API의 실패는 상태·최종 URL·응답 일부를 가진 `BrowserHttpFailure`로 전달해 쿠팡 수집기가 정확한 `Job not found` 응답만 해석한다.

작업 결과 `CLOSED`는 `COMPLETED`에 속한다. `GET /api/collections/{id}/tasks`의 `closed` 및 진단 메시지(`error`)로 판별 결과를 확인할 수 있다. 기업별 결과의 `closed`는 성공 처리된 마감 공고 수로, `succeeded`에 포함된다. 본문이 달라지지 않았음을 확인한 `IDENTICAL`과 구별한다. 수집 후 변화 요약의 새로 마감된 공고·관심 공고도 같은 트랜잭션에서 기록한다.

2026-11-21 마감 처리 이후 경력 자료(V12), 공고별 지원서 원문(V13, V15~V17), 추천·맞춤 초안(V14), 공고별 지원 준비(V18)가 추가되었다. 현재 백업은 스키마 18이며 지원 건·문항 답변·맞춤 이력서/포트폴리오·면접 조사·경험 인터뷰를 포함한다. 스키마 10~17 백업은 지원 준비 기록이 없으므로 복원 미리보기에 누락을 알린다. 오늘의집 목록은 제목과 지원 URL이 동시에 없는 작성 중 항목만 건너뛰도록 보완했다. 정상 공고의 필수 정보 누락 오류는 숨기지 않는다.

대표 화면 확인 후 외부 접속을 차단한 HTML/SSR 재현, localhost 브라우저 응답, 임시 MySQL 저장 프로브로 검증했다. 실제 사용자 DB의 수집·재시도는 실행하지 않았고, 원래 실패 기록과 공고 상태를 유지했다. 실패 재시도의 실제 동작 확인은 사용자가 수행한다.

## 저장형 AI 질문과 선택 워커

상세 계약은 [`docs/ai-chat.md`](../docs/ai-chat.md)와 [제품 스펙](../docs/spec.md#전체-공고-ai-질문)을 따른다. `aiqa`는 domain 모델·저장 포트, application의 대화/공고 검색/작업 이벤트 서비스, JPA 어댑터, REST API와 별도 Kotlin CLI 워커로 나뉜다. V11은 대화·메시지·작업 큐·이벤트 로그·출처 테이블을 만든다.

`POST /api/ai/conversations/{id}/messages`는 사용자 질문과 비어 있는 답변, 대기 작업을 저장하고 `202`로 `{taskId, messages}`를 즉시 반환한다. Codex가 오프라인이어도 대기 가능하며, `POST /api/ai/worker/tasks/claim`은 `FOR UPDATE SKIP LOCKED`로 대기 작업을 하나 임대한다. 작업자는 10초 간격으로 임대를 갱신한다. 임대가 만료되면 메시지의 부분 답변을 비우고 다시 대기시키며 SSE에 복구 상태를 남긴다.

`GET /api/ai/tasks/{taskId}/events?after={eventId}`는 DB에 저장된 상태·검색·답변 조각·완료·실패 이벤트를 SSE로 재생한 뒤 실시간 이벤트를 보낸다. 이벤트는 트랜잭션 커밋 후 현재 구독자에 전송한다. 연결이 끊긴 구독자는 제거하되 이벤트 저장이나 워커 작업은 실패 처리하지 않는다. 다시 접속하면 마지막 이벤트 ID 이후의 저장 이벤트를 재생한다. 15초 keepalive와 NGINX `proxy_buffering off`로 오랜 응답과 재연결을 지원한다. 브라우저 연결과 Codex 프로세스는 서로 독립적이다.

작업자는 로컬 Kotlin 프로세스로 실행한다. Java 21 이상, Codex CLI 설치 및 `codex login`이 필요하다. `./start.sh`는 Docker 앱이 준비된 뒤 워커 시작을 자동 시도하며, 자격·CLI·Java가 준비되지 않아도 Docker 앱은 계속 제공한다. macOS에서는 `launchd`가 워커를 관리해 시작 셸이 종료된 뒤에도 프로세스를 유지한다. 실패 원인은 `./ai-worker.sh status`와 `backend/storage/ai-worker.log`에서 확인하고, 준비 후 `./ai-worker.sh start|restart|stop`으로 워커만 관리할 수 있다. 시작·재시작은 추적 PID와 같은 워커 클래스의 고아 프로세스를 정리하고, 실행별 새 `workerId`가 `/api/ai/status` heartbeat에 도착해야 성공으로 처리한다. 이전 `connected=true` heartbeat만으로 새 프로세스 적용을 판정하지 않는다. 기존 `./start.sh --ai-worker`도 호환을 위해 허용하며 기본 동작과 같다. `./stop.sh`는 워커와 Docker 앱을 함께 종료한다. Gradle의 `aiWorker` 실행 작업도 제공한다. 워커와 Spring 애플리케이션의 Kotlin `main()`이 별도이므로 Spring Boot `mainClass`는 `JobMoaApplicationKt`로 지정한다.

2026-11-22 이전 동작 검증: 시작·상태 스크립트가 worker ID 파일을 정상 판독하는 것을 확인하고 최신 백엔드/프론트 이미지로 재기동했다. macOS `launchd` 워커는 시작 셸 종료 후 50초가 지나도 PID와 heartbeat가 유지됐으며, 새 백엔드가 당시 대기 지원서 분석 2건을 처리했다. V16에서 지원서 분석 큐와 기능을 제거했다. 이전 이미지에서 발생하던 작업 claim 404는 최신 백엔드 재빌드 후 더 이상 나타나지 않았다. 실제 공고 수집은 실행하지 않았고 데이터 볼륨은 초기화하지 않았다.

Codex CLI `app-server`를 stdio JSON-RPC로 실행해 `gpt-6-luna` 임시 대화에서 답변 조각 알림(`item/agentMessage/delta`)을 받는다. 셸 도구·MCP 서버·플러그인을 끄고 읽기 전용 샌드박스·승인 생략 설정으로 실행한다. `web_search="live"` 설정으로 질문에 필요한 경우에만 외부 검색을 허용하며, 출처 URL은 HTTP(S) 주소로 검증해 저장한다. 공고 검색은 모든 수집 공고(마감 포함)의 메타데이터·직군·최신 본문을 조회하고, 키워드 점수 상위 12건의 본문 발췌(각 6,000자까지)를 작업 컨텍스트에 보관한다. 대화 요약·답변·공고/웹 출처는 MySQL에 저장하고 외부 검색 페이지 본문은 저장하지 않는다. 네이티브 공고 projection의 읽음 여부는 `readFlag` 별칭으로 매핑해 Kotlin Boolean `isRead` getter의 프로퍼티 이름 변환 문제를 피한다.

AI 작업이 수행할 수 있는 상태 변경 도구는 없다. 완료된 답변에만 출처를 저장하며, 모델이 선택한 공고 ID는 작업 시점의 검색 컨텍스트와 대조한다. 2026-11-21 기능 브랜치의 격리 환경에서 실제 질문·웹 검색·답변 조각·출처 표시를 브라우저로 확인했다. 검색 도중 화면을 나가 SSE를 끊어도 작업이 완료되고, 재접속 시 저장 답변과 출처가 표시됐다. 단일 작업의 저장 이벤트는 `search` 1개, `delta` 9개, `complete` 1개였고 백엔드 오류 로그는 없었다. 실제 공고 수집은 실행하지 않았다. 백엔드는 `./gradlew compileKotlin`과 격리 Docker 이미지 빌드를 통과했다.

| API | 용도 |
| --- | --- |
| `GET /api/ai/status` | 선택 워커 heartbeat와 고정 모델 표시 |
| `GET/POST /api/ai/conversations` | 대화 목록·새 대화 |
| `GET/DELETE /api/ai/conversations/{id}` | 메시지와 출처를 포함한 대화 조회·삭제 |
| `POST /api/ai/conversations/{id}/messages` | 질문 저장, 비동기 작업 ID 반환 |
| `POST /api/ai/messages/{messageId}/retry` | 실패 답변 재대기 |
| `GET /api/ai/tasks/{taskId}/events` | SSE 상태·웹 검색·답변 조각·최종 이벤트 구독/재생 |
| `/api/ai/worker/**` | 선택 로컬 작업자의 heartbeat·작업 임대·진행 저장 API |
