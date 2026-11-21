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
- 새 사이트 9곳: 쏘카(공식 목록+Greeting 상세), 컬리·무신사·직방(Greeting), 야놀자(Workday), 리디(RoundHR), 채널톡(자체 공개 페이지), 여기어때(인턴 공고 페이지), 뱅크샐러드(공식 목록+Greeting 상세). 구현 범위와 사이트별 실제 확인은 [직무 지원표](../docs/job-categories.md) 및 각 `source-*.md` 문서를 따릅니다.

이 목록은 구현된 공개 포털 범위이며 모든 법인의 존재/채용 여부를 보증하는 법인 목록은 아닙니다. 최신 등록 범위는 `/api/sources`의 `portals`에서 확인합니다. 공고가 0건인 포털도 정상 수집 결과이며, 구조 변경은 실패로 기록합니다.

실행 버튼 한 번에 기록 하나를 만들고, 각 계열사 포털의 목록 조회는 독립 작업으로 저장합니다. 한 포털이 실패해도 다른 포털과 공고 처리는 계속하며, 목록 재시도는 실패한 포털만 다시 실행합니다. 공고는 공통 MySQL 큐에 넣고 빈 작업자가 가져갑니다.

작업자는 기본 3개(`CRAWLER_WORKERS`), 사이트별 활성 페이지 작업은 기본 1개(`CRAWLER_SITE_CONCURRENCY`)입니다. 별도 목록 탐색 스레드 1개도 사이트 제한을 공유합니다. 이는 페이지 작업 제한이며 이미지·CSS 등 모든 하위 리소스 요청의 개수 제한은 아닙니다. `site-overrides`로 호스트별 동시성, `minimum-navigation-interval-overrides`로 호스트별 요청 시작 간격을 설정합니다. 현재 기본값은 `www.coupang.jobs` 5초, `boards-api.greenhouse.io` 3초이며, 이는 429가 발생하지 않는다고 검증된 한계값이 아니라 요청을 분산하기 위한 보수적 시작값입니다. 쿠팡의 상세 API 응답도 호스트 제한 대상입니다.

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
- `GET /api/state`, `/api/postings`, `/api/postings/{id}`: 각 공고의 `categories`(복수 가능)와 `classification` 제공. 아직 분류하지 않은 과거 데이터는 `classification: null`.
- 공고별 비관적 잠금과 짧은 트랜잭션으로 최신 본문을 읽고 연결을 교체합니다. 수집·읽음 변경과 같은 공고 잠금을 사용하며, 개별 실패는 목록으로 반환합니다. 재분류는 브라우저를 만들지 않습니다.

실제 Kotlin 결과 평가 도구: `java -cp 'backend/build/classes/kotlin/main:backend/build/resources/main:backend/build/runtime-libs/*' scripts/analysis/EvaluateKotlinClassifier.java /path/corpus.jsonl /tmp/predictions.json` (루트에서 빌드 후 JDK 21 이상). 고정 정답과 Python 해석기 대조 결과는 [구현 검증](../docs/analysis/classification-implementation-v0.5.md)에 기록합니다.

## 관심 공고 API

`interest` 영역은 도메인 `PostingInterest`와 저장 포트, Spring Data JPA 구현으로 구성합니다. Flyway V8의 `posting_interest`에 공고 ID당 한 행을 두고 수집 결과/본문 버전과 분리합니다. 목록 조회는 관심 정보를 한 번에 읽어 N+1을 피합니다. `/api/state`, `/api/postings`, `/api/postings/{id}`의 `interest`에는 `starred`, `note`, `priority`, `applicationStatus`, `appliedOn`, `updatedAt`을 제공합니다. 저장 이력이 없으면 관심 해제·빈 메모·미지정·지원 전·지원일 없음이 기본값입니다.

| 요청 | 본문 | 동작 |
| --- | --- | --- |
| `PATCH /api/postings/{id}/interest` | `{"starred":true}` | 별표만 변경. 메모·지원 기록 유지 |
| `PUT /api/postings/{id}/interest/management` | `{"note":"확인할 내용","priority":"HIGH","applicationStatus":"APPLIED","appliedOn":"2026-11-15"}` | 개인 관리 필드 저장. 별표 유지 |

두 쓰기 API는 갱신한 `interest` 객체를 반환합니다. 우선순위는 `HIGH/NORMAL/LOW/NONE`, 상태는 `BEFORE_APPLY/APPLIED`입니다. 메모 최대 5,000자. 완료 상태에서 지원일 생략 시 기존 지원일 또는 서울 시간 오늘을 사용합니다. 지원 전으로 저장할 때 지원일은 `null`이어야 하며, 미래 지원일과 길이 초과는 400, 존재하지 않는 공고는 404입니다. 잘못된 enum/날짜 형식은 요청 파싱에서 400 처리됩니다.

공고의 비관적 잠금을 잡고 기존 관리 정보를 읽어 수정하므로 최초 행 생성·별표·메모·읽음·수집 저장이 공고 단위로 직렬화됩니다. 별표 해제에 행 삭제를 사용하지 않습니다. 수집기와 저장 본문 재분류는 `posting_interest`를 갱신하지 않습니다. 마이그레이션은 기존 공고/캡처/기록을 삭제하지 않습니다.

2026-11-16 검증: Flyway V8 적용과 실제 DB/API에서 등록·해제·재등록 보존·지원 전 복구·동시 최초 저장·입력 오류(400/404)·기존 공고/버전/분류/기록 보존을 확인했습니다. 임시 관리 정보는 검증 후 원복했습니다. 실제 재수집을 통한 검증은 실행하지 않았습니다. [검증 범위](../docs/interest-postings-spec.md).

## 개인 데이터 관리·프리셋·변화 요약 API

설계와 삭제 범위는 [`docs/workspace-tools-spec.md`](../docs/workspace-tools-spec.md)를 따른다. V9는 프리셋·실행별 변화·작업공간 리비전을, V10은 한 실행의 다중 직군 범위를 추가한다. 일반 CRUD는 Spring Data JPA를 사용하며, 전체 백업/복원과 일괄 초기화만 고정 테이블 목록의 JDBC 배치와 트랜잭션을 사용한다. FK를 끄거나 TRUNCATE하지 않는다.

| API | 용도 |
| --- | --- |
| `GET /api/collection-presets` | 저장된 선택 목록 |
| `POST /api/collection-presets` | `{name, categoryIds, companyIds} (단일 categoryId도 허용)` 저장 |
| `PUT /api/collection-presets/{id}` | 선택과 이름 변경 |
| `DELETE /api/collection-presets/{id}` | 프리셋 삭제 |
| `POST /api/collection-presets/{id}/start` | 저장된 조합으로 수집 실행 |
| `GET /api/collections/{id}/changes` | 신규·변경·마감 및 당시 관심 공고 변화의 ID 목록, 과거 기록 여부 |
| `GET /api/workspace` | 데이터 건수·캡처 용량 |
| `POST /api/workspace/reset` | `{scope: interests\|postings\|read\|presets, confirmation: "초기화"}` |
| `POST /api/workspace/captures/cleanup` | `{confirmation: "정리"}`로 미사용 캡처 삭제 |
| `GET /api/workspace/backup` | ZIP 다운로드, 수집 중 409 |
| `POST /api/workspace/backups/preview` | multipart `file` 업로드, 검증 후 토큰·건수 반환 |
| `POST /api/workspace/backups/{token}/restore` | `{confirmation: "복원"}`, 현재 전체 데이터 교체 |
| `DELETE /api/workspace/backups/{token}` | 미리보기 취소 및 임시 파일 삭제 |

백업 형식은 `jobmoa` v2 / DB 스키마 10 (복원은 v1·v2 지원), ZIP의 `workspace.json`과 `captures/{sha256}.png`로 구성된다. 현재 스키마가 다른 백업은 거부한다. ZIP 2 GiB·해제 4 GiB·개별 파일/JSON 128 MiB 제한이다. 임시 파일은 `${STORAGE_DIRECTORY}/workspace-tmp`에 저장하고 만료·재시작 잔여물을 정리한다. Spring multipart와 Docker 프론트 NGINX의 업로드 크기/타임아웃을 함께 조정했다. 백업 생성은 파일 스트리밍을 사용하지만 스냅샷 JSON·DB 행은 메모리에 보관하므로 한도까지의 대규모 복원 성능은 별도 검증이 필요하다.

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

추가 결과값을 포함한 백업은 v2로 내보내며 복원은 v1/v2를 지원한다(DB 스키마 10 유지). 오늘의집 목록은 제목과 지원 URL이 동시에 없는 작성 중 항목만 건너뛰도록 보완했다. 정상 공고의 필수 정보 누락 오류는 숨기지 않는다.

대표 화면 확인 후 외부 접속을 차단한 HTML/SSR 재현, localhost 브라우저 응답, 임시 MySQL 저장 프로브로 검증했다. 실제 사용자 DB의 수집·재시도는 실행하지 않았고, 원래 실패 기록과 공고 상태를 유지했다. 실패 재시도의 실제 동작 확인은 사용자가 수행한다.
