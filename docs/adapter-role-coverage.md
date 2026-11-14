# 기업별 개발 직무 후보 수집 범위

아래 규칙은 목록 발견과 상세 추출에서 동일하게 적용하는 기업별 후보 판별이다. 최종 분야 판별은 회사·분야별 수집 전략에서 수행한다. 따라서 후보 범위를 늘려도 웹 백엔드에 풀스택·데이터·ML·DevOps·SRE 공고가 함께 저장되는 것은 아니다.

## NAVER

- 공식 `Tech` 직군을 필수로 확인한다. 기존 Back-end·Front-end·Android·iOS·Software Development·DevOps·SRE 직무와 함께 Backend·Frontend 표기를 처리한다.
- `Tech / 공통`처럼 세부 직무가 포괄적일 때도 제목에 명시된 Android/iOS 개발, 풀스택, 데이터·ML·AI 엔지니어, DevOps, Site Reliability Engineer/SRE를 후보로 전달한다. 본문의 단순한 `개발` 언급으로 후보를 늘리지 않는다.
- 인재풀, Scientist, Research Engineer, Analyst, QA, 보안, 게임 서버·클라이언트, 하드웨어, 비개발 직무는 제외한다. 임베디드 등 기존 전체 개발 수집 범위는 유지하고 최종 분야 전략에서 구분한다.
- 근거: 저장해 둔 NAVER 통합·웹툰 목록 JSON에서 `Tech / 공통`, `Tech / Backend` 및 기존 회사별 직무 표기를 확인했다. 새로운 분야의 공고가 현재 없더라도 0건으로 처리할 수 있도록 명확한 직함을 후보 기준에 추가했다.

## Toss

- 공식 Job Category의 Backend·Frontend·App·Data Engineering·ML·Infra·Device·병역특례와 개발자/엔지니어/DevOps/SRE 직함을 함께 확인하는 기존 구조를 유지한다. 이 분류에서 이미 신규 분야 후보 대부분을 발견할 수 있어 범용 본문 필터나 새로운 공식 카테고리를 가정하지 않았다.
- 저장된 API에서 Android, Frontend/FE Platform, Data Engineer, ML Engineer, DevOps, SRE 공고가 각각 해당 공식 카테고리에 포함된 것을 확인했다. Backend 분류에도 DevOps/SRE가 있으므로 최종 분야를 공식 분류 하나로 단정하지 않는다.
- 인재풀·게임 공고를 명시적으로 제외하고 QA·Scientist·Analyst·보안·IDC·네트워크·어시스턴트 등 기존 제외 조건을 유지한다.

## Kakao

- 카카오 본사: 공식 `TECHNOLOGY` 직군에서 기존 Android/iOS/Web front/Server/Hadoop 기술에 `Algorithm_ML`을 추가했다. Scientist·Research·Analyst·QA·보안·게임·인재풀은 제외하므로 ML 기술 태그가 연구·분석 공고를 그대로 통과시키지 않는다.
- 카카오뱅크: 기존 `Engineering`만 허용하면 실제 API의 `Server`, `Core Banking`, `Data`, `AI` 개발 공고가 누락된다. 이 공식 직군과 명확한 개발·엔지니어 직함을 함께 확인한다. 저장된 목록의 백엔드·도메인 개발자, 데이터 엔지니어, AI 엔지니어, DevOps가 근거다.
- 카카오페이증권: 저장된 Ninehire 목록에서 확인한 `DevOps`·`AI 엔지니어링`을 기존 백엔드·프론트엔드·모바일·데이터 개발 직무 허용 목록에 추가했다. 풀스택 및 SRE처럼 명확한 개발 직무명도 후보로 처리한다.
- 카카오스타일: 공식 `Tech` 직군의 Backend/Frontend/Android/iOS 후보를 유지한다. Data/Infra에서는 데이터·AI·ML 엔지니어와 MLOps/DevOps/SRE처럼 직함이 명확한 경우만 허용해 품질 운영·비즈니스 분석 공고를 제외한다.
- 카카오VX: `개발` 직군의 개발 직함, 또는 `기술` 직군의 명확한 DevOps/SRE·데이터·AI/ML 개발 직무와 엔지니어 직함을 확인한다. 저장된 DevOpsTF 공고의 공식 분류는 `기술 / DevOps`다.
- Greeting 계열사: 모빌리티의 `개발` 직무, 엔터프라이즈의 `Tech`, 페이·엔터테인먼트의 공식 개발/기술 직군 등 각 회사의 기존 필수 조건을 유지한다. 모빌리티·엔터프라이즈의 제목 후보를 데이터·ML·모바일·풀스택·DevOps/SRE로 보완하고, 페이손해보험·디케이테크인의 명확한 개발 직무명을 확장한다. 키즈노트의 `개발 / 개발` 기준은 유지한다.
- Greeting 공통 제외식에서 `PM`·`HW`·`QA`는 영문자와 붙어 있는지 확인한다. 한글과 붙은 `QA엔지니어`·`개발PM`은 계속 제외한다. 영문 `Development` 속 `pm` 같은 문자열 때문에 개발 직함이 잘못 제외되는 일을 방지한다. 이 제외식은 카카오 계열 어댑터 내부 규칙이며 다른 기업에 적용하지 않는다.
- 새로운 사이트나 비개발 분야는 추가하지 않았다. 외부 요청을 새로 실행하지 않고 기존 `/tmp/jobmoa-toss-kakao`의 공식 HTML/API 응답만 참고했다.

## Ninehire 재오픈 공고의 마감 판별

2026-11-14 실제 버튼 검증에서 카카오페이증권 SRE 공고는 `status=in_progress`, `reception.canSubmit=true`, `endAt=null`인데 과거 `closedAt`이 남아 있었다. `closedAt` 존재만으로 마감 처리하면 다시 열린 공고를 숨기므로 현재 종료 상태(`closed`/`finished`/`ended`) 또는 명시적인 `endAt` 경과로만 마감을 판별한다. 이 규칙은 같은 Ninehire 상세 경로를 사용하는 카카오스타일·카카오VX에도 적용한다. 기존 저장 공고는 해당 회사 재수집 시 상태를 갱신하며, 본문이 같으면 새 버전·캡처를 만들지 않는다.
