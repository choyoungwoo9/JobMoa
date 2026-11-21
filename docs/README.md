# 프로젝트 문서 안내와 유지 기준

이 문서는 프로젝트 문서의 **주제별 기준 위치**, 변경 시 갱신할 문서, 새 문서를 추가하는 방법을 안내한다. 새 문서를 만들기 전에 여기서 기존 기준 문서를 확인한다.

## 문서의 기준 역할

문서는 같은 사실을 여러 곳에 복사하지 않고, 각 문서가 맡은 질문에 답한다.

| 알고 싶은 내용 | 기준 문서 |
| --- | --- |
| 제품이 제공해야 할 동작과 합의된 범위 | [제품 스펙](spec.md) |
| 설치·실행·화면 사용 흐름 | [루트 README](../README.md) |
| API·백엔드 구조·수집 큐·설정·운영 | [백엔드 README](../backend/README.md) |
| 프론트 구조·화면·개발 방법 | [프론트엔드 README](../frontend/README.md) |
| 회사×직군 지원 조합과 현재 분류 방식 | [직무 지원표](job-categories.md) · [분류 규칙](classification-rules.md) |
| 크롤러 신규 구현·변경 절차 | [크롤러 어댑터 표준](crawler-adapter-standard.md) |
| 사이트별 후보 수집 규칙과 실제 지원 범위 | [수집·선별 규칙](crawler-selection-rules.md) 및 아래 사이트별 문서 |
| 통계의 정의·기간·한계 | [채용 통계](statistics.md) |
| 관심 공고 관리 | [관심 공고 스펙](interest-postings-spec.md) |
| 백업·복원·초기화·프리셋 등 개인 데이터 관리 | [개인 데이터 관리 스펙](workspace-tools-spec.md) |
| 전체 공고 AI 질문·대화·스트리밍 계약 | [AI 질문과 Codex 워커](ai-chat.md) |
| 저장된 공고의 사이트별 마감 판별 | [마감 판별 기준](closure-detection.md) |

제품의 합의된 범위는 `docs/spec.md`가 상위 기준이다. 세부 문서는 해당 기능의 판정 조건과 예외를 설명하고, 실제 코드·API·화면의 현재 동작은 구현과 모듈 README에서 확인한다. 서로 다른 설명을 발견하면 한쪽을 임의로 기준 삼지 말고, 변경 작업에서 사용자 합의와 실제 동작을 확인해 함께 바로잡는다.

## 문서 목록

### 제품과 사용 기능

| 문서 | 역할과 상태 |
| --- | --- |
| [spec.md](spec.md) | 현재 제품 동작과 합의된 범위 |
| [statistics.md](statistics.md) | 통계 지표·필터·관측 기간의 정의와 한계 |
| [interest-postings-spec.md](interest-postings-spec.md) | 관심 공고·메모·우선순위·지원 상태 상세 |
| [workspace-tools-spec.md](workspace-tools-spec.md) | 개인 데이터 초기화·백업·복원·프리셋·변화 요약·비교 |
| [ai-chat.md](ai-chat.md) | 전역 AI 대화·비동기 작업·SSE 재연결·Codex 워커 계약 |

### 수집기와 지원 범위

| 문서 | 역할과 상태 |
| --- | --- |
| [crawler-adapter-standard.md](crawler-adapter-standard.md) | 새 사이트와 어댑터를 추가·수정할 때의 공통 절차 |
| [crawler-selection-rules.md](crawler-selection-rules.md) | 현재 포털의 후보 선별 방식과 기업군별 구현 규칙 |
| [job-categories.md](job-categories.md) | 회사×직군 지원 조합, 분류 분야와 제외 경계 |
| [adapter-role-coverage.md](adapter-role-coverage.md) | 네이버·토스·카카오 계열 개발 공고의 후보 수집 범위 |
| [adapter-role-coverage-extra.md](adapter-role-coverage-extra.md) | 쿠팡·당근·LINE·우아한형제들 개발 공고의 후보 범위 |
| [non-development-sources.md](non-development-sources.md) | 기획·PM·디자인·마케팅·영업 후보를 제공하는 사이트와 조건 |
| [source-bucketplace.md](source-bucketplace.md) | 오늘의집 목록·상세 수집 규칙 |
| [source-socar.md](source-socar.md) | 쏘카·나인투원 공식 목록 및 Greeting 상세 규칙 |
| [source-kurly.md](source-kurly.md) | 컬리 Greeting 테크 공고 규칙 |
| [source-musinsa.md](source-musinsa.md) | 무신사·5개 자회사별 공고 규칙 |
| [source-yanolja.md](source-yanolja.md) | 야놀자 Workday 공개 목록 규칙 |
| [source-ridi.md](source-ridi.md) | 리디 RoundHR API 규칙 |
| [source-channel.md](source-channel.md) | 채널톡 공개 채용 데이터 규칙 |
| [source-yeogi.md](source-yeogi.md) | 여기어때 채용 페이지 접근 상태·후보 규칙 |
| [source-banksalad.md](source-banksalad.md) | 뱅크샐러드 공식 목록 및 Greeting 상세 규칙 |
| [source-zigbang.md](source-zigbang.md) | 직방 Greeting 목록 규칙 |
| [closure-detection.md](closure-detection.md) | 사이트별 종료 증거, 오탐 방지와 실제 확인 범위 |

### 분류 규칙과 고정 분석 자료

| 문서 | 역할과 상태 |
| --- | --- |
| [classification-rules.md](classification-rules.md) | 신규·저장 공고에 실제 적용하는 일반 분류 규칙 |
| [classification-proposal.md](classification-proposal.md) | 2026-11-15 승인·구현 전 분석의 역사 기록. 현재 제품 상태와 규칙은 위의 스펙·분류 규칙을 기준으로 한다. |
| [analysis/classification-440-2026-11-15.md](analysis/classification-440-2026-11-15.md) | 당시 저장 공고 440개의 고정 검토 스냅샷 |
| [analysis/classification-440-2026-11-15.json](analysis/classification-440-2026-11-15.json) | 위 검토표의 구조화 데이터 |
| [analysis/classification-gold-v1.json](analysis/classification-gold-v1.json) | 고정 정답셋 버전과 해시를 가리키는 매니페스트 |
| [analysis/classification-implementation-v0.5.md](analysis/classification-implementation-v0.5.md) | 당시 규칙 구현 점검의 범위와 결과 |
| [analysis/classification-implementation-v0.5.json](analysis/classification-implementation-v0.5.json) | 구현 점검의 구조화 결과 |
| [analysis/classification-evaluation-v0.5.md](analysis/classification-evaluation-v0.5.md) | 고정 정답셋과 분류 규칙의 오프라인 비교 결과 |
| [analysis/classification-evaluation-v0.5.json](analysis/classification-evaluation-v0.5.json) | 오프라인 비교의 구조화 결과 |

`analysis/` 자료는 특정 시점의 재현·평가를 위한 스냅샷이다. 현재 운영 데이터 수나 현재 외부 사이트 상태로 표현하지 않는다. 제품 규칙이 바뀌어도 과거 실험 결과는 임의로 덮어쓰지 않고 새 버전 자료를 만든 뒤 현재 기준 문서에서 연결한다.

화면 캡처 자산은 [`docs/images/`](images/)에 둔다. 로고의 정적 자산과 출처는 [`frontend/public/logos/README.md`](../frontend/public/logos/README.md)를 따른다.

## 변경 영향표

기능을 바꾸기 전에 변경이 아래 어느 영역에 해당하는지 확인하고, 영향받은 기준 문서만 코드와 같은 작업·커밋에서 갱신한다.

| 변경 영역 | 함께 확인할 기준 문서 |
| --- | --- |
| 사용자에게 보이는 기능·제품 정책 | `docs/spec.md`; 사용 흐름 변화가 있으면 루트 `README.md`와 `frontend/README.md` |
| 프론트 화면·상태·탐색 흐름 | `frontend/README.md`; 합의된 제품 동작도 달라지면 `docs/spec.md`; 사용법 변화가 있으면 루트 `README.md` |
| API·도메인·DB 스키마·백업 형식 | `backend/README.md`; 사용자 동작이나 보존 범위가 달라지면 `docs/spec.md`와 관련 기능 스펙 |
| 회사·포털·직군 지원 범위 및 분류 조건 | `job-categories.md`, `classification-rules.md`, 관련 수집기 문서와 `docs/spec.md` |
| 사이트별 탐색·추출·마감 처리 | `crawler-adapter-standard.md`, 해당 사이트 규칙 문서, `backend/README.md`; 제품 동작 변경 시 `docs/spec.md` |
| 실행·배포·포트·환경 설정 | 관련 모듈 README와 루트 `README.md`; 사용자에게 약속한 실행 방식 변경 시 `docs/spec.md` |
| 통계·기간 계산·집계 의미 | `statistics.md`, 필요하면 `docs/spec.md`와 API 설명 |
| 실제 사이트/API/화면 검증 | 관련 문서에 확인 날짜·대상·실행 범위·결과·미확인 범위를 기록 |

단순 내부 리팩터링처럼 사용자 동작·API·데이터·운영 절차·지원 범위가 유지되면 문서를 억지로 바꾸지 않는다. 대신 작업 보고에서 문서 갱신이 불필요했던 이유를 밝힌다.

## 새 문서를 만들 때

1. `docs/README.md`와 저장소 전체에서 같은 주제의 기준 문서가 있는지 찾는다. 기존 문서의 범위를 보완할 수 있으면 새 파일 대신 그 문서를 갱신한다.
2. 새 파일이 필요하면 **소유할 질문 하나**를 정한다. 상위 정책·제품 범위와 세부 구현·검증 기록을 한 문서에 뒤섞지 않는다.
3. 문서 첫 부분에 현재 기준인지, 제안인지, 특정 시점의 검증·분석 기록인지 표시한다. 특정 날짜의 데이터·수치·사이트 확인을 적으면 날짜와 범위를 함께 적는다.
4. 코드에서 결정되는 값은 코드를 기준으로 삼고 문서에는 이유·예외·지원 범위·검증 결과를 기록한다. 같은 표나 규칙을 여러 문서에 복사하지 말고 기준 문서로 링크한다.
5. 새 문서를 이 인덱스에 추가하고 관련 상위 문서에서도 링크한다. 문서 변경은 관련 코드·설정과 같은 검토 가능한 커밋에 포함한다.
6. 링크와 상태를 검토한다. 과거 자료가 현재 동작처럼 보이지 않는지, 검증하지 않은 범위를 검증 완료로 쓰지 않았는지 확인한다.

현재 문서 간 의미 일치 여부는 변경 영향표와 작업 검토로 확인한다. 문서 변경이 없다는 사실만으로 코드 변경이 잘못됐다고 판단하지 않으며, 자동화는 링크 유효성이나 코드로부터 만들 수 있는 표처럼 기계적으로 판별 가능한 부분부터 추가한다.
