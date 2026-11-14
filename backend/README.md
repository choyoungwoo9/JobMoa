# 백엔드

Kotlin · Spring Boot · Spring Data JPA · MySQL 8.4. Java 21 이상을 사용합니다.

```sh
# 루트에서 DB만 실행
docker compose up -d --wait mysql
cd backend
./gradlew bootRun
```

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

현재 단계는 저장소·트랜잭션·API 기반 구현입니다. 사이트 수집기와 작업자 실행기는 아직 연결하지 않았고, 미구현 수집기는 시작 요청을 거부합니다. 프론트도 아직 샘플 데이터로 동작합니다.
다음 단계에서 네이버 수집기 하나를 연결하고, 사용자 확인 후 나머지 기업군을 확장합니다.

설정은 `src/main/resources/application.yaml`을 참고하세요. DB 스키마는 Flyway가 관리하며 JPA는 `validate`만 수행합니다. DB·캡처 Docker 볼륨은 일반 종료 시 삭제하지 않습니다.
