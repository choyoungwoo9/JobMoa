# 백엔드

Kotlin · Spring Boot · Spring Data JPA · MySQL 8.4. Java 21 이상을 사용합니다.

```sh
# 루트에서 DB만 실행
docker compose up -d --wait mysql
cd backend
BROWSER_CHANNEL=chrome ./gradlew bootRun
```

로컬 실행은 설치된 Chrome을 재사용합니다. Playwright 내장 Node가 구형 macOS에서 실행되지 않으면 `PLAYWRIGHT_NODEJS_PATH`에 실행 가능한 Node 22 경로를 지정하세요. Docker에는 호환 Chromium과 한글 글꼴이 포함됩니다.

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
| GET | `/api/state` | 공고·수집 기록·수집기 지원 상태 |
| GET | `/api/postings` | 공고 목록 및 저장 버전 |
| GET | `/api/postings/{id}` | 공고 상세 |
| PATCH | `/api/postings/{id}/read` | `{ "read": true }` |
| GET | `/api/collections` | 최근 실행 30개 |
| GET | `/api/collections/{id}` | 기업별 결과와 실패 항목 |
| GET | `/api/collections/{id}/tasks` | 작업별 상태·실패 단계 |
| POST | `/api/collections` | `{ "sourceIds": ["naver"] }` |
| POST | `/api/collections/{id}/retry` | `{ "failureIds": ["..."] }`, 생략 시 미해결 실패 전체 |

수집기는 7개 기업군별로 구현되어 있으며, 판별 규칙은 각 수집기 내부에 있습니다. 공통 실행부에는 개발 직군 키워드 필터가 없습니다.

- 네이버: 통합·클라우드·SNOW/KREAM·랩스·웹툰·파이낸셜·I&S·Z.
- 토스: 통합 포털의 실제 계열사별 개별 포지션.
- 카카오: 본사, 페이·뱅크·페이증권·모빌리티·엔터프라이즈·엔터테인먼트·스타일·VX·페이손해보험·디케이테크인·키즈노트 별도 포털.
- 쿠팡: 그룹 공식 Greenhouse 전체 보드. 현재 원문 HTTP 403은 본문 실패로 남기며 차단 화면을 공고로 저장하지 않습니다.
- 당근: 당근·당근페이 통합, 당근서비스, 북미·일본 포털.
- LINE: LINE Careers 전 지역, LY 일본, LY Communications.
- 우형: 우아한형제들·우아한청년들.

이 목록은 구현된 공개 포털 범위이며 모든 법인의 존재/채용 여부를 보증하는 법인 목록은 아닙니다. 최신 등록 범위는 `/api/sources`의 `portals`에서 확인합니다. 공고가 0건인 포털도 정상 수집 결과이며, 구조 변경은 실패로 기록합니다.

실행 버튼 한 번에 기록 하나를 만들고, 각 계열사 포털의 목록 조회는 독립 작업으로 저장합니다. 한 포털이 실패해도 다른 포털과 공고 처리는 계속하며, 목록 재시도는 실패한 포털만 다시 실행합니다. 공고는 공통 MySQL 큐에 넣고 빈 작업자가 가져갑니다.

작업자는 기본 3개(`CRAWLER_WORKERS`), 사이트별 활성 페이지 작업은 기본 1개(`CRAWLER_SITE_CONCURRENCY`)입니다. 별도 목록 탐색 스레드 1개도 사이트 제한을 공유합니다. 이는 페이지 작업 제한이며 이미지·CSS 등 모든 하위 리소스 요청의 개수 제한은 아닙니다. `site-overrides` 설정으로 호스트별 한도를 바꿀 수 있습니다.

각 작업자는 자기 스레드에서만 Playwright와 브라우저를 생성·재사용·정리합니다. 작업별 컨텍스트는 분리합니다. 임대 토큰과 주기적 갱신으로 중복 처리를 방지하고, 중단된 공고 작업은 임대 만료 후 체크포인트부터 다시 처리합니다. 브라우저 탐색·본문·캡처는 DB 트랜잭션 밖에서 수행합니다.

목록·진행률 조회에는 본문을 포함하지 않습니다. `/api/postings/{id}`에서 본문을 읽고, 캡처는 `/api/captures/{해시}.png`에서 선택적으로 불러옵니다. 변경된 공고만 새 버전을 저장하고 이전 버전은 보관합니다. 저장된 진행 중 공고도 다음 수집에 다시 확인하며, 명시적인 마감만 보관 처리합니다.

설정은 `src/main/resources/application.yaml`을 참고하세요. DB 스키마는 Flyway가 관리하며 JPA는 `validate`만 수행합니다. DB Docker 볼륨과 `backend/storage/captures`는 일반 종료 시 삭제하지 않습니다.
