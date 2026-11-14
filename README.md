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

기본 화면은 실제 API와 MySQL을 사용합니다. 네이버·토스·카카오·쿠팡·당근·LINE·우아한형제들과 연결된 계열사 포털을 선택해 수집할 수 있습니다. 사이트별로 개발 공고를 판별하며, 공통 큐와 작업자가 본문·캡처를 저장합니다. [백엔드 안내](backend/README.md).

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

샘플 화면은 `http://127.0.0.1:15173/?demo=1`에서 별도로 확인합니다. 실제 API에 연결하지 못하면 오류를 표시하며 샘플 데이터로 자동 전환하지 않습니다. [화면 안내](frontend/README.md), [확정 스펙](docs/spec.md).

쿠팡은 공식 목록을 조회할 수 있으나 현재 원문이 HTTP 403을 반환해 본문 단계에서 실패할 수 있습니다. 실패 기록에서 URL과 오류를 확인할 수 있습니다. 전 세계 모든 법인의 채용 사이트가 누락 없이 등록된 상태를 뜻하지는 않으며, 등록된 포털은 `/api/sources`의 `portals`에서 확인합니다.
