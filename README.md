# JobMoa

개인용 로컬 개발자 채용공고 수집·관리 도구입니다.

```text
JobMoa/
├── frontend/       # React · TypeScript · Vite
├── backend/        # Kotlin · Spring Boot · JPA · MySQL
├── docs/           # 스펙 및 설계
└── compose.yaml    # Docker 실행 구성
```

## 전체 실행·종료

Docker Desktop을 실행한 뒤 루트에서:

```sh
./start.sh
./stop.sh
```

시작할 때 기존 구성을 내리고, **15173·18080·13306** 포트를 사용하는 컨테이너와 프로세스를 종료한 뒤 다시 빌드·실행합니다. 일반 종료 후 남아 있는 프로세스는 강제 종료합니다. DB와 캡처 볼륨은 보존합니다. 5173과 3307은 정리 대상이 아닙니다.

| 서비스 | 주소 |
| --- | --- |
| 화면 | http://127.0.0.1:15173 |
| 백엔드 | http://127.0.0.1:18080 |
| MySQL | `127.0.0.1:13306` |

화면은 아직 샘플 데이터를 사용합니다. 백엔드는 DDD 구조와 Spring Data JPA 저장소·트랜잭션·API 기반을 구현한 상태이며, 실제 수집기 연결은 다음 단계입니다. [백엔드 안내](backend/README.md).

## 프론트 개발 서버

```sh
cd frontend
npm ci
npm run dev
```

주소: http://127.0.0.1:15173 . 포트가 사용 중이면 자동으로 변경하지 않고 종료합니다.

Docker로 전체 실행 중이라면 먼저 `./stop.sh`로 포트를 비우세요. DB·백엔드를 Docker로 유지하면서 프론트만 개발하려면:

```sh
docker compose stop frontend
docker compose up --build -d backend
cd frontend
npm run dev
```

현재 프론트는 샘플 데이터로 화면을 검토할 수 있습니다. 실제 수집 기능은 백엔드에서 구현 중입니다. [화면 안내](frontend/README.md), [확정 스펙](docs/spec.md).
