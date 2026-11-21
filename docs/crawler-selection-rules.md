# 채용공고 수집 및 개발 직군 판별 규칙

기준일: 2026-11-21, 한국 시간.

이 문서는 현재 구현된 수집기의 동작을 설명한다. 향후 목표인 ‘전체 계열사 수집’과 현재 등록된 포털 범위를 구분한다. 수집기의 구현 여부와 실제 사이트에서 수집이 성공했는지도 별개다.

신규 사이트 구현·기존 어댑터 변경 순서는 [크롤러 어댑터 표준](crawler-adapter-standard.md)을 따른다. 이 문서는 현재 후보 선별 규칙과 실제 지원 범위를 기록한다.

## 1. 공통 처리 방식

1. 등록된 채용 포털에서 공고 목록을 가져온다.
2. 회사별 직군·직무·제목 규칙으로 수집 대상을 고른다.
3. 선택한 공고를 공통 작업 큐에 넣는다.
4. 작업자가 상세 페이지를 열고 해당 수집기의 판별 규칙을 다시 확인한다.
5. 조건을 통과하면 본문·캡처를 처리하고 결과를 저장한다.

개발 직군 판별에는 공식 분류 정보와 제목의 정규식·허용 목록을 사용한다. 본문의 실제 업무 내용을 의미 분석하거나 AI로 분류하는 기능은 없다. 본문 추출·렌더링 검증은 개발 직군 판별과 별도로 수행한다.

별도의 개인 연차·기술 스택 필터는 없다. 국내 근무만 허용하는 공통 필터도 없으며 LINE·LY와 Karrot 해외 포털도 대상에 포함되어 있다. 고용 형태만으로 정규직·계약직·인턴을 일괄 제외하는 공통 규칙은 없다.

대부분의 제목 정규식은 대소문자를 무시한다. 직군·직무 분류의 문자열 허용 목록은 코드에 적힌 값과 정확히 비교하므로 표기 차이에 영향을 받는다.

## 2. 기업군별 요약

| 기업군 | 핵심 포함 조건 | 주요 제외 조건 | 코드 |
| --- | --- | --- | --- |
| 네이버 | `Tech` 직군이고 허용 직무 또는 개발 제목 키워드에 해당 | 인재풀, QA·보안·하드웨어·연구·기획 등 제목 | [NaverDeveloperRule][naver] |
| 토스 | 허용 Job Category이고 제목에 개발·엔지니어 키워드 포함 | 인재풀, 소속 미상·기타, QA·분석·보안·IT 운영 등 | [TossDeveloperRule][toss] |
| 카카오 본사 | `TECHNOLOGY` 직군이고 개발 스킬 태그 또는 개발 제목에 해당 | 비공개·마감 공고, 연구·QA·보안·기획 등 | [KakaoDeveloperRule][kakao] |
| 카카오 계열사 | 각 포털의 직군·직무·제목 조합 | 포털 종류별 제외 제목. 상세 조건은 5절 참조 | [계열사 판별 함수][kakao-affiliates] |
| 쿠팡 | 허용 기술 조직이고 제목이 구체적인 소프트웨어 개발 직무 | QA·보안·하드웨어·분석·Manager·Director 등 | [CoupangDeveloperRule][coupang] |
| 당근 국내 | 제목이 `Software Engineer` 또는 `Site Reliability Engineer`로 시작 | 해당 제목 형식에 맞지 않는 공고 | [DaangnDeveloperRule][daangn] |
| 당근서비스·해외 | 별도 포털의 개발 제목 허용 규칙 | 해외는 QA·보안·분석·관리자 등 추가 제외 | [당근서비스][daangn-service], [해외][karrot-global] |
| LINE | `Engineering` 직군이고 개발 세부 분야 또는 개발 제목에 해당 | QA·분석·PM·Engineering Manager·지원·보안 운영 등 | [LineDeveloperRule][line] |
| 일본 LY 계열 | 개발 직무 태그 또는 일본어·영어 개발 제목 규칙 | 테스트·보안·분석·관리·지원 등 | [LY Corporation][ly], [LY Communications][ly-communications] |
| 우아한형제들 | 형제들은 Tech 코드와 개발 제목, 청년들은 개발 제목 | QA·보안·DBA·분석·기획·디자인 등 | [WoowaDeveloperRule][woowa] |
| 쏘카 | 공식 `개발/데이터` 직군 코드와 개발 제목 | 하드웨어·품질·분석·기획 등 | [쏘카 규칙][socar] |
| 컬리 | 공식 테크 부문, 개발·인프라 직군, 구체적 개발 제목 | PM·디자인·보안·분석·비개발 등 | [컬리 규칙][kurly] |
| 무신사 | 회사별 포털 필터와 공식 기술 직무명 | QA·보안·분석·기획·비개발 등 | [무신사 규칙][musinsa] |
| 야놀자 | Workday 공개 목록에서 구체적 개발 직함 | Product Owner·PM·QA·분석·비개발 등 | [야놀자 규칙][yanolja] |
| 리디 | RoundHR 진행 중 + 제품/개발 직군 + 기술 직함 | PD·MD·디자인·분석·운영 등 | [리디 규칙][ridi] |
| 채널코퍼레이션 | 공개 목록의 명확한 소프트웨어·인프라·ML 직함 | 보안·영업·지원·분석·비개발 등 | [채널톡 규칙][channel] |
| 여기어때 | Greeting의 공식 `기술` 직군과 명확한 개발 직함 | 공식 Greeting 목록·Android 상세 1건 읽기 전용 확인; 전체 수집 미검증 | [여기어때 규칙][yeogi] |
| 뱅크샐러드 | 공식 테크 목록 링크 + 개발 직함 | PM·분석·사이언티스트·비개발 등 | [뱅크샐러드 규칙][banksalad] |
| 직방 | Greeting 공식 조직 필드 + 개발 직함 | 세일즈·분석·하드웨어·비개발 등 | [직방 규칙][zigbang] |

## 3. 네이버

수집 범위: 네이버 통합 포털, 네이버클라우드, SNOW·KREAM, 네이버랩스, 네이버웹툰, 네이버파이낸셜, 네이버 I&S, 네이버 Z의 등록된 채용 포털.

다음 조건을 모두 만족해야 한다.

- 직군 `classCdNm`이 정확히 `Tech`다.
- 제목이 인재풀 패턴에 해당하지 않는다. `인재풀`, `인재 pool`, `Talent Pool` 등의 표기를 검사한다.
- 제목에 제외 키워드가 없다.
- 세부 직무 `subJobCdNm`이 허용 목록에 있거나 제목이 개발 키워드에 해당한다.

허용 직무명은 `Embedded SW`, `Back-end`, `Front-end`, `Android`, `iOS`, `Software Development`, `DevOps`, `SRE`다.

대표적인 제목 허용 키워드는 Software Engineer, Firmware Engineer, Backend, Frontend, Full Stack, Server Developer·Engineer, 백엔드, 프론트엔드, 개발자, 개발 엔지니어다. 정확한 변형 허용 범위는 소스의 정규식을 따른다.

제외 제목 키워드는 Hardware, Mechanical, 보안, Security, QA, Test Engineer, Scientist, Research Engineer, 기획, 디자이너, 사업, Sales, Contents Developer다.

주의할 현재 동작:

- 직군이 `Tech 외`인 통합 공고는 통과하지 않는다. 공고 안에 개발 직무가 있어도 같다.
- `Backend`·`Frontend`는 허용 목록의 `Back-end`·`Front-end`와 정확히 일치하지 않는다. 이 경우 제목 조건으로 통과할 수는 있다.
- `AI/ML`·`Data Engineering`은 세부 직무 허용 목록에 없다. 제목 조건에 해당해야 한다.

## 4. 토스

수집 범위: 통합 포털의 공고 묶음을 실제 계열사별 포지션으로 펼친 결과.

다음 조건을 모두 만족해야 한다.

- 개별 포지션의 `internal_job_id`가 존재한다.
- 인재풀 메타데이터에 해당하지 않는다. 상세 페이지에서도 `isProspect`를 확인한다.
- 회사명이 존재하며 `기타`가 아니다.
- Job Category가 `Backend`, `Frontend`, `App`, `Data Engineering`, `ML`, `Infra`, `Device`, `병역특례` 중 하나다.
- 제목에 `개발`, `엔지니어`, `Engineer`, `Developer`, `DevOps`, `SRE` 중 하나가 있다.
- 제목에 제외 키워드가 없다.

제외 제목 키워드는 QA, Quality, Test Engineer, Scientist, Analyst, 분석가, 기획, 디자인, 디자이너, 어시스턴트, Assistant, IT 운영, IT 담당, IDC, Network, Security, 보안, Technical Writer, 제품 관리자다.

`Infra` 분류 전체를 허용하는 것은 아니다. 제목 조건을 함께 만족해야 하며 네트워크·IDC·보안 등의 제목은 제외한다.

## 5. 카카오

### 5.1 카카오 본사

목록은 기술 직군 API에서 가져오고 비공개·마감 공고를 제외한다. 개발 직군 판별 조건은 다음과 같다.

- `jobType`이 정확히 `TECHNOLOGY`다.
- 제목에 제외 키워드가 없다.
- 스킬 태그가 허용 목록에 하나 이상 있거나 제목에 개발 키워드가 있다.

허용 스킬 태그는 `Android`, `iOS`, `Windows`, `Web_front`, `Server`, `Hadoop_eco_system`이다.

제목 허용 키워드는 개발, Developer, Software, Engineer, 엔지니어, DevOps, SRE다. 제외 키워드는 Scientist, Research, 연구, Analyst, 분석가, QA, Quality, Test Engineer, 보안, Security, 기획, 디자인, 운영 담당 계열 표현이다.

### 5.2 Greeting 포털 계열사

아래 계열사는 공개 배포된 공고이며 마감 시각이 지나지 않은 경우에 목록에서 검토한다. 각 행의 분류 조건과 제목 조건을 모두 적용한다.

공통 제목 제외: 인재풀, Scientist, Research, QA, 품질, 보안, Security, HW, 하드웨어, 테크니션, 기획, 디자이너, 디자인, PM, 분석가, 컨설턴트, Assistant, 어시스턴트.

| 계열사 | 분류 조건 | 제목 조건 |
| --- | --- | --- |
| 카카오페이 | 직군이 `개발·기술·테크·Tech` 중 하나 | 개발·Engineer·엔지니어·DevOps·SRE |
| 카카오모빌리티 | 세부 직무에 `개발` 포함 | 개발자·개발 엔지니어·백엔드·프론트엔드·서버 개발·소프트웨어·SLAM·AI 엔지니어·시스템 엔지니어 계열 |
| 카카오엔터프라이즈 | 직군에 `Tech` 포함 | 개발자·개발 엔지니어·백엔드·프론트엔드·Software·DevOps·SRE |
| 카카오엔터테인먼트 | 직군이 `개발·기술·Tech·테크·개발/기술` 중 하나 | 개발·Engineer·엔지니어·DevOps·SRE |
| 카카오페이손해보험 | 기술·개발 계열 직군이고 허용 개발 세부 직무에 해당 | 개발·Engineer·엔지니어·DevOps·SRE |
| 디케이테크인 | 직군이 `Backend·Frontend·Android·iOS` 중 하나 | 개발·Engineer·엔지니어 |
| 키즈노트 | 직군과 세부 직무에 각각 `개발` 포함 | 개발자·Engineer·엔지니어 |

카카오페이손해보험의 허용 세부 직무는 `Backend`, `Frontend`, `Server`, `서버개발`, `클라이언트개발`, `시스템개발`, `인프라개발`, `DevOps`, `SRE`다.

카카오게임즈는 판별 함수의 분기만 존재하며 현재 `KakaoAffiliatePortals.targets`에는 등록되어 있지 않다. 따라서 현재 자동 목록 수집 범위로 세지 않는다. 해당 분기는 `개발` 직군과 `데이터개발·어플리케이션개발·웹서비스개발·플랫폼개발` 세부 직무를 허용한다.

### 5.3 카카오뱅크

- 직군 `recruitClassName`이 정확히 `Engineering`이어야 한다.
- 제목에 개발·Engineer·엔지니어·DevOps·SRE 중 하나가 있어야 한다.
- 제목에 QA, 보안, Security, 품질, 기획, 분석가, Scientist, Research, 네트워크, DBA, IT 운영, 인재풀이 있으면 제외한다.
- 목록에서는 접수 종료 시각이 지난 공고도 제외한다.

### 5.4 Ninehire 포털 계열사

공통 제목 제외: QA, 보안, Security, 기획, 분석가, 인재풀, 디자이너, 품질, 운영 매니저 계열 표현. Greeting 포털의 제외 목록과 동일하지 않다.

| 계열사 | 포함 조건 |
| --- | --- |
| 카카오페이증권 | 직군이 `백엔드 개발·프론트엔드 개발·서버 개발·Android 개발·iOS 개발·데이터 엔지니어링·플랫폼 개발·개발` 중 하나. 별도 제목 허용 키워드는 요구하지 않는다. |
| 카카오스타일 | 직군이 `Tech`이고 세부 직무가 `Backend·Frontend·Android·iOS`, 또는 세부 직무가 `Data·Infra`이면서 제목이 MLOps·DevOps·SRE·데이터 엔지니어·Data Engineer에 해당 |
| 카카오VX | 직군이 `개발`이고 제목에 개발·프로그래머·Engineer 포함, 또는 직군이 `기술`이고 세부 직무가 `DevOps·SRE` |

## 6. 쿠팡

수집 범위: 쿠팡 공식 Greenhouse 공개 채용 보드. 다음 조건을 모두 만족해야 한다.

- 제목에 허용된 소프트웨어 개발 직무 표현이 있다.
- 부서명 중 하나가 허용 기술 조직이거나 문자열이 ` Tech`로 끝난다.
- 제목에 제외 키워드가 없다.

대표 허용 제목은 Software Engineer·Developer, Backend, Frontend, Full Stack, Android·iOS·Machine Learning·Data·AI·Platform·Site Reliability Engineer, SRE, 소프트웨어 개발·엔지니어, 백엔드, 프론트엔드, 서버 개발, 데이터 엔지니어다.

허용 기술 조직은 `Technology`, `eCommerce Engineering`, `Tech Infrastructure`, `Search and Discovery`, `Catalog Tech`, `Coupang Intelligent Cloud (CIC)`, `Global Operations Technology (Global Ops Tech)`, `Corporate IT`, `Marketing System`, `Supernova`다.

제외 제목은 QA, SDET, Test Engineer, Security, Hardware, Mechanical, Scientist, Analyst, Manager, Director, Recruiter, Sales, 품질, 보안, 기계, 설비, 채용, 영업, 물류 운영 계열 표현이다. 기술 조직의 공고라도 관리자·분석가 등 제외 제목에 걸리면 수집하지 않는다.

상세 페이지의 보안 확인 화면은 공고로 저장하지 않고 수집 실패로 기록한다.

## 7. 당근

### 7.1 국내 통합 포털

제목이 `Software Engineer` 또는 `Site Reliability Engineer`로 시작해야 한다. 앞에 `[태그]`가 붙은 형태도 허용한다.

이에 따라 `Software Engineer, Backend`, `Software Engineer, Frontend`, Android, iOS, Machine Learning, Data 등의 제목을 포함할 수 있다. `Data Analytics Engineer`, `Security Engineer`, `Network Engineer`, `Test Automation Engineer`처럼 다른 형태로 시작하는 제목은 이 규칙에 해당하지 않는다.

국내 규칙에는 별도의 QA·보안·관리자 제목 제외 정규식이 없다. 예를 들어 제목이 `Software Engineer`로 시작하면 뒤쪽의 다른 표현만으로 추가 제외하지 않는다.

### 7.2 당근서비스

공개 배포된 공고 중 제목이 다음 허용 직무명으로 시작해야 한다.

- Software Engineer, Software Developer
- Product Engineer (ERP)
- Backend Engineer, Frontend Engineer, Full Stack Engineer
- 백엔드 개발자, 프론트엔드 개발자

### 7.3 Karrot 북미·일본

영어·일본어 소프트웨어 개발 제목 키워드로 판단한다. Software Engineer·Developer, Backend·Frontend·Full Stack Engineer·Developer, Android·iOS·Machine Learning·Data·Site Reliability Engineer, 일본어 소프트웨어·백엔드·프론트엔드 엔지니어 표현을 허용한다.

QA, SDET, Security, Hardware, Mechanical, Scientist, Analyst, Manager, Director, Sales 및 일본어 테스트·보안 표현이 제목에 있으면 제외한다.

## 8. LINE 및 일본 LY 계열

### 8.1 LINE Careers

- `job_unit`에 정확히 `Engineering`이 포함되어야 한다.
- 제목에 제외 키워드가 없어야 한다.
- 세부 분야가 허용 목록에 있거나 제목에 개발 키워드가 있어야 한다.

허용 분야는 `Client-side`, `Web Development`, `Server-side`, `Data Engineering`과 코드에 남아 있는 과거 Front-end·Desktop/C++·Game Engineering·Android·iOS·Blockchain 태그다. 과거 태그에는 `(Not in use)` 등의 접두사가 포함된 정확한 문자열을 사용한다.

제목 허용 키워드는 Software, Backend, Frontend, Full Stack, Android, iOS, Server Engineer, Data Engineer, DevOps, 개발자, 소프트웨어 등이다. SRE는 뒤에 Dev·Engineer가 나오는 형태를 검사한다.

제외 제목은 QA, SET, Test·Testing, Quality, Scientist, Analyst, Product Manager, Project Manager, Engineering Manager, Team Lead, Technical Partner, Security Professional, Security Operations, Incident Response, Support Engineer, 테스트, 품질, 분석가, 기획, 디자이너다.

### 8.2 LY Corporation 일본

목록에서 일본어 직군이 `エンジニアリング`이고 개발 직무 태그가 허용 목록에 있어야 한다. 허용 태그 ID는 `j-0-0`, `j-0-1`, `j-0-2`, `j-0-6`, `j-0-7`이다.

일본어·영어 분석가, Scientist, 지원, 관리자·관리 업무, Director, Consultant, Coach, 보안, 품질, 테스트, Staff Lead 등의 제외 제목도 검사한다. 상세 페이지에서도 직무 태그와 제목을 재확인한다.

### 8.3 LY Communications

일본어·영어 소프트웨어, 백엔드, 프론트엔드, 서버사이드, 네이티브 앱, 프로그래머, 개발 엔지니어, Android, iOS, 데이터 엔지니어 등의 제목 키워드를 허용한다.

테스트·품질·지원·관리자·관리 업무·Director·Consultant·관리직·개발 PM·분석가·Scientist·보안·운영·유지보수·QA·PM·PL 등의 제목은 제외한다.

## 9. 우아한형제들 및 우아한청년들

우아한형제들은 직군 코드가 `BA005001`이어야 한다. 우아한청년들은 이 코드를 사용하지 않고 제목 조건을 적용한다. 같은 코드가 청년들 포털에서는 재무 직군이므로 두 포털의 직군 코드를 공유하지 않는다.

두 포털 모두 다음 제목 조건을 적용한다.

- 소프트웨어, 백엔드, 프론트엔드, 서버·웹·앱·플랫폼 개발, 플랫폼엔지니어링, 데이터엔지니어링, 머신러닝 엔지니어, 개발자, Software, Backend, Frontend, Full Stack, Android, iOS, DevOps, SRE, Data Engineer, Machine Learning Engineer 계열 키워드를 허용한다.
- QA, Test·Testing, Quality, 테스트, 품질, 정보보호, 정보보안, 침해, 보안관제, 데이터베이스, DBA, Scientist, Analyst, 데이터분석, 프로덕트 매니저, 기획, 디자인, 영업 계열 제목을 제외한다.

## 10. 마감·제외·실패의 구분

- 개발 직군 규칙에서 탈락한 공고는 목록 단계에서 작업 큐에 들어가지 않거나, 상세 단계에서 제외 결과로 기록한다.
- 목록 형식 변경, 본문 추출 실패, 캡처 실패, 보안 확인 등은 ‘비개발 공고’와 구분하여 실패로 처리한다.
- 비공개·마감·배포 상태의 목록 단계 제외 조건은 포털마다 다르다. 예를 들어 카카오 본사는 마감·비공개 플래그, Greeting은 배포 상태·마감 시각, 카카오뱅크는 접수 종료 시각을 확인한다.
- 상세 페이지에서 명시적인 마감 상태나 종료 시각을 확인하는 로직도 별도로 존재한다. 목록에서 사라졌거나 수집에 실패했다는 이유만으로 마감을 확정하는 규칙은 아니다.

## 11. 현재 기준의 차이와 누락 가능성

| 항목 | 현재 동작 | 검토할 점 |
| --- | --- | --- |
| 통합 채용공고 | 네이버 `Tech 외`는 제외 | 개발 포지션이 명시된 통합 공고를 포함할지 |
| 직무명 표기 | 정확한 분류 문자열 비교 | Backend/Back-end 등 공식 표기 변형 대응 |
| AI·데이터·인프라 | 토스·쿠팡 등은 허용 범위가 있고 네이버는 제목 조건에 의존 | 공통적으로 포함할 개발 직무 범위 |
| 관리자 | 쿠팡·Karrot 해외·LY 등은 명시적으로 제외 | 다른 포털에도 같은 정책을 적용할지 |
| 인재풀 | 네이버·토스·일부 카카오 포털에 명시적 제외 규칙 | 전 수집기에 동일 정책이 필요한지 |
| 당근 국내 | 허용 직무명 접두사만 검사 | 허용 접두사 뒤에 QA·보안·관리 표현이 붙은 경우 |
| 제목 정규식 | 일부는 단어 경계 없이 부분 문자열 검사 | 정상 개발 제목의 오제외 가능성 |
| 계열사 등록 | 구현된 분기와 실제 대상 등록이 다를 수 있음 | 카카오게임즈처럼 미등록인 분기의 추가 여부 |

이 표는 변경이 필요한 항목을 자동 확정하지 않는다. 정책을 결정하면 해당 수집기와 이 문서를 함께 갱신한다.

## 관련 코드

- [수집기 등록과 기업군 목록][registry]
- [네이버][naver]
- [토스][toss]
- [카카오 본사][kakao] / [카카오 계열사][kakao-affiliates]
- [쿠팡][coupang]
- [당근 국내][daangn] / [당근서비스][daangn-service] / [Karrot 해외][karrot-global]
- [LINE][line] / [LY Corporation][ly] / [LY Communications][ly-communications]
- [우아한형제들·우아한청년들][woowa]
- [쏘카][socar] / [컬리][kurly] / [무신사][musinsa]
- [야놀자][yanolja] / [리디][ridi] / [채널코퍼레이션][channel]
- [여기어때][yeogi] / [뱅크샐러드][banksalad] / [직방][zigbang]

[registry]: ../backend/src/main/kotlin/com/youngwoo/jobmoa/crawler/application/SourceRegistry.kt
[naver]: ../backend/src/main/kotlin/com/youngwoo/jobmoa/crawler/infrastructure/site/NaverRecruitmentSite.kt
[toss]: ../backend/src/main/kotlin/com/youngwoo/jobmoa/crawler/infrastructure/site/toss/TossRecruitmentSite.kt
[kakao]: ../backend/src/main/kotlin/com/youngwoo/jobmoa/crawler/infrastructure/site/kakao/KakaoRecruitmentSite.kt
[kakao-affiliates]: ../backend/src/main/kotlin/com/youngwoo/jobmoa/crawler/infrastructure/site/kakao/KakaoAffiliatePortals.kt
[coupang]: ../backend/src/main/kotlin/com/youngwoo/jobmoa/crawler/infrastructure/site/coupang/CoupangRecruitmentSite.kt
[daangn]: ../backend/src/main/kotlin/com/youngwoo/jobmoa/crawler/infrastructure/site/daangn/DaangnRecruitmentSite.kt
[daangn-service]: ../backend/src/main/kotlin/com/youngwoo/jobmoa/crawler/infrastructure/site/daangn/DaangnServicePortal.kt
[karrot-global]: ../backend/src/main/kotlin/com/youngwoo/jobmoa/crawler/infrastructure/site/daangn/KarrotGlobalPortals.kt
[line]: ../backend/src/main/kotlin/com/youngwoo/jobmoa/crawler/infrastructure/site/line/LineRecruitmentSite.kt
[ly]: ../backend/src/main/kotlin/com/youngwoo/jobmoa/crawler/infrastructure/site/line/LyRecruitmentPortal.kt
[ly-communications]: ../backend/src/main/kotlin/com/youngwoo/jobmoa/crawler/infrastructure/site/line/LyCommunicationsPortal.kt
[woowa]: ../backend/src/main/kotlin/com/youngwoo/jobmoa/crawler/infrastructure/site/woowa/WoowaRecruitmentSite.kt
[socar]: source-socar.md
[kurly]: source-kurly.md
[musinsa]: source-musinsa.md
[yanolja]: source-yanolja.md
[ridi]: source-ridi.md
[channel]: source-channel.md
[yeogi]: source-yeogi.md
[banksalad]: source-banksalad.md
[zigbang]: source-zigbang.md
