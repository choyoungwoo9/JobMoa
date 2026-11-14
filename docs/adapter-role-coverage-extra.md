# 쿠팡·당근·LINE·우아한형제들 직무 후보 수집 범위

## 적용 범위

웹 프론트엔드, Android, iOS, 풀스택, 데이터 엔지니어, ML 엔지니어, DevOps/SRE를 추가할 때 회사별 기존 목록·상세 규칙이 명확한 개발 공고를 먼저 버리지 않도록 보완했다. 목록과 상세 추출은 각 포털의 같은 판별 함수를 사용한다. 이 문서는 후보 수집 단계의 범위이며, 직무 카테고리의 최종 판정은 `CategoryClassifier`가 담당한다.

`Software Engineer`, `Mobile Engineer`, `Platform Engineer` 같은 직함을 후보로 허용해도 이를 특정 직무로 곧바로 분류하지 않는다. 직무를 명확히 판별할 수 없는 경우 세부 분야에서 제외한다. Scientist, Analyst, QA, Security, DBA는 이번 추가 직무에 포함하지 않는다.

## 회사별 변경

| 기업·포털 | 공식 근거 및 보완 | 제한 |
| --- | --- | --- |
| 쿠팡 | 기존 Greenhouse 공식 기술 조직 조건을 유지하면서 `DevOps Engineer`, `Mobile Engineer`, `ML Engineer`, `Data Engineering` 및 명확한 한국어 개발 직함을 후보로 허용한다. | 기술 조직 소속과 개발 직함을 모두 요구한다. 저장 자료의 `Senior DevOps Engineer (SPIR/OC)`는 보안 조직 소속이어서 여전히 제외한다. `Data Engineering Lv.4`도 Retail Selection 소속이면 제외한다. 보안·과학자·분석가·QA·관리직은 포함하지 않는다. |
| 당근 국내 | 기존 `Software Engineer`, `Site Reliability Engineer` 한정 규칙에 독립적인 Backend/Frontend/Fullstack, Android/iOS, Data/ML/AI, DevOps 직함을 추가한다. 직함 앞의 공식 법인 표기 및 Senior/Staff 등 경력 표기를 허용한다. | 단순 `Engineer`, 직무를 드러내지 않는 운영/비개발 명칭까지 확대하지 않는다. 과학자·분석가·QA·보안 직함은 명시적으로 제외한다. |
| 당근서비스 | Greeting 목록과 상세에서 같은 명확한 개발 직함 규칙을 적용한다. 모바일·데이터·ML·DevOps/SRE 독립 직함을 보완한다. 기존 `Product Engineer (ERP)` 후보는 유지한다. | 서비스 포털에 없는 공고를 다른 포털에서 대신 생성하지 않는다. 현재 해당 분야 공고가 없을 수 있다. |
| Karrot 북미·일본 | 영어 DevOps/SRE, ML/AI, Mobile 개발 직함과 일본어 풀스택·데이터·기계학습·DevOps/SRE 직함을 보완한다. | 국가별 공식 목록과 기존 국내 중복 URL 통합만 사용한다. 보안·과학자·분석가·QA는 제외한다. |
| LINE Careers | 공식 `Engineering` 직군 조건과 기존 세부 개발 직무를 유지한다. 세부 태그가 비어 있어도 명확한 Machine Learning/ML/AI, Site Reliability/SRE 직함은 후보로 남긴다. | `Data Engineering` 태그가 붙은 DBA를 개발자로 수집하지 않도록 DBA/Database Administrator를 명시적으로 제외했다. Security 직함도 제외한다. 인프라·네트워크 직함만으로 DevOps/SRE를 추정하지 않는다. |
| LY Corporation 일본 | 저장한 공식 역할 태그에서 확인한 `j-0-3 = 機械学習エンジニア`, `j-0-8 = SRE`를 기존 프론트엔드·백엔드·앱·데이터·DevOps 허용 태그에 추가한다. | `j-0-4` 데이터 사이언티스트, `j-0-9` 인프라, `j-0-11` 보안, `j-0-16` QA를 추가하지 않는다. 포괄적 인프라 직무를 SRE로 분류하지 않는다. |
| LY Communications | 일본어 풀스택·기계학습, 영어 ML/AI, DevOps/SRE 직함을 보완한다. 명확한 DevOps/SRE 직함에 `運用`·`保守`가 함께 쓰였다는 이유만으로 제외하지 않는다. | DevOps/SRE 근거가 없는 운영·보수 직함, QA·보안·분석가·관리직은 기존처럼 제외한다. |
| 우아한형제들·청년들 | 기존 Tech 조건 및 포털별 직군 코드 차이를 유지한다. `데이터 엔지니어링`, `머신 러닝 엔지니어`처럼 띄어쓰기가 있는 한국어 표기, 풀스택·모바일·AI·DevOps/SRE 직함을 보완한다. | 형제들의 `BA005001` Tech 코드를 청년들에 그대로 적용하지 않는다. DBA·정보보안·Scientist/Analyst·QA는 계속 제외한다. |

## 검증과 남은 범위

- 외부 사이트에 추가 요청하지 않고 기존 `/tmp/jobmoa-line-data.json`, `/tmp/jobmoa-ly-jobs-latest.html`, `/tmp/jobmoa-coupang.json`, `/tmp/jobmoa-woowa-jobs.json`을 확인했다.
- Kotlin 메인 컴파일과 JShell에서 직접 호출한 회사별 판별 함수의 포함·제외 사례를 확인했다. 테스트 소스 파일은 추가하지 않았다.
- 실제 수집 버튼을 통한 소량 검증은 루트 작업에서 수행한다. 후보 규칙을 추가했다고 모든 포털에 현재 그 직무 공고가 존재하는 것은 아니다.
- 새로운 사이트, 비개발 직무, Scientist/Analyst/QA/Security 카테고리는 구현하지 않았다.
