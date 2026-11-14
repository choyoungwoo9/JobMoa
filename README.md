# JobMoa

개인용 로컬 개발자 채용공고 수집·관리 도구입니다.

```text
JobMoa/
├── frontend/       # React · TypeScript · Vite
├── backend/        # Kotlin · Spring Boot · JPA · MySQL
├── docs/           # 스펙 및 설계
└── compose.yaml    # Docker 실행 구성
```

## 프론트 실행

```sh
cd frontend
npm ci
npm run dev
```

주소: http://127.0.0.1:15173 . 포트가 사용 중이면 자동으로 변경하지 않고 종료합니다.

프론트 Docker 실행:

```sh
docker compose up --build -d frontend
```

현재 프론트는 샘플 데이터로 화면을 검토할 수 있습니다. 실제 수집 기능은 백엔드에서 구현 중입니다. [화면 안내](frontend/README.md), [확정 스펙](docs/spec.md).
