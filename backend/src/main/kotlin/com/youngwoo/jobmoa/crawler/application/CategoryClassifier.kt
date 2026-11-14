package com.youngwoo.jobmoa.crawler.application

import com.youngwoo.jobmoa.crawler.domain.model.DiscoveredPosting
import com.youngwoo.jobmoa.crawler.domain.model.ExtractedPosting
import org.springframework.stereotype.Component

/**
 * 기존 사이트 어댑터의 공식 직무 분류를 통과한 결과에만 분야별 규칙을 더한다.
 * 회사 × 분야를 키로 전략을 등록하므로 새 회사/분야는 지원 범위와 규칙을 함께 추가해야 한다.
 */
@Component
class CategoryClassifier(private val companies: CompanyRegistry) {
    private val strategies: Map<Pair<String, String>, JobPolicy> = companies.list().flatMap { company ->
        company.supportedCategoryIds.filter { it != "all" }.map { categoryId ->
            val policy = if (categoryId in BUSINESS_CATEGORIES && company.sourceId in setOf("toss", "daangn")) businessPolicy(company.sourceId, categoryId)
            else if (company.sourceId == "bucketplace") bucketplacePolicy(categoryId)
            else if (categoryId == "web-backend") when (company.sourceId) {
                "naver" -> naverPolicy(company.id)
                "toss" -> tossPolicy(company.id)
                "kakao" -> kakaoPolicy(company.id)
                "coupang" -> coupangPolicy(company.id)
                "daangn" -> daangnPolicy(company.id)
                "line" -> linePolicy(company.id)
                "woowa" -> woowaPolicy(company.id)
                else -> error("웹 백엔드 회사 전략이 없습니다: ${company.id}")
            } else fieldPolicy(company.id, company.sourceId, categoryId)
            (company.id to categoryId) to policy
        }
    }.toMap()

    fun supports(companyId: String, categoryId: String): Boolean =
        companies.find(companyId)?.supportedCategoryIds?.contains(categoryId) == true &&
            (categoryId == "all" || strategies.containsKey(companyId to categoryId))

    /** 후보 단계에서 불명확한 공고의 상세 페이지와 캡처를 불필요하게 요청하지 않는다. */
    fun candidate(categoryId: String, companyId: String, posting: DiscoveredPosting): Boolean {
        if (!supports(companyId, categoryId)) return false
        if (categoryId == "all") return true
        return strategies.getValue(companyId to categoryId).candidate(posting.title)
    }

    /** 본문에 '개발'이 있다는 이유로 포함하지 않는다. 회사별 직무명과 업무 근거가 필요하다. */
    fun accepts(categoryId: String, companyId: String, posting: ExtractedPosting): Boolean {
        if (!supports(companyId, categoryId)) return false
        if (categoryId == "all") return true
        return strategies.getValue(companyId to categoryId).accepts(posting)
    }
}

private data class JobPolicy(
    val titleRoles: Regex,
    val bodyEvidence: Regex,
    val excludedRoles: Regex,
    val additionalBodyEvidence: Regex? = null,
) {
    fun candidate(title: String): Boolean = titleRoles.containsMatchIn(title) && !excludedRoles.containsMatchIn(title)
    fun accepts(posting: ExtractedPosting): Boolean = candidate(posting.title) &&
        posting.bodyText.length >= 40 && bodyEvidence.containsMatchIn(posting.bodyText) &&
        (additionalBodyEvidence == null || additionalBodyEvidence.containsMatchIn(posting.bodyText))
}

// 제외 대상은 분야 계약이다. 프론트엔드와 협업한다는 본문 문장으로 백엔드 공고를 제외하지 않는다.
private const val NOT_BACKEND = "full[ -]?stack|풀\\s*스택|front[ -]?end|프론트\\s*엔드|devops|dev[ -]?ops|\\bsre\\b|site reliability|data[ -]?(?:engineer|engineering)|데이터\\s*엔지니어|data\\s*platform|데이터\\s*플랫폼|machine[ -]?learning|머신러닝|\\bmlops\\b|\\bml engineer\\b|AI\\s*엔지니어|게임\\s*서버|game\\s*server|\\bqa\\b|\\bsdet\\b|test\\s*engineer|security\\s*engineer|보안\\s*엔지니어|network\\s*engineer|네트워크\\s*엔지니어|hardware|하드웨어|embedded|임베디드|firmware|펌웨어|(?:android|ios)\\s*(?:engineer|developer)|안드로이드|인재\\s*(?:풀|pool)|talent\\s*pool|バックエンド以外|フルスタック|フロントエンド|ゲームサーバー|データエンジニア|SREエンジニア"
private fun regex(expression: String) = Regex(expression, RegexOption.IGNORE_CASE)

// NAVER의 Back-end/Software Development 분류를 선행 조건으로 사용하고 서비스 서버 개발을 확인한다.
private fun naverPolicy(companyId: String): JobPolicy = JobPolicy(
    titleRoles = regex(when (companyId) {
        "naver-cloud" -> "back[ -]?end|백엔드|서버\\s*(?:개발|엔지니어)|(?:웹|서비스)\\s*(?:API|서버)"
        "naver-financial" -> "back[ -]?end|백엔드|서버\\s*(?:개발|엔지니어)|(?:결제|금융)\\s*서비스\\s*개발"
        "naver-webtoon" -> "back[ -]?end|백엔드|서버\\s*(?:개발|엔지니어)|server[ -]?(?:developer|engineer)"
        else -> "back[ -]?end|백엔드|서버\\s*(?:개발|엔지니어)|server[ -]?(?:developer|engineer)|웹\\s*서비스\\s*개발"
    }),
    bodyEvidence = regex("(?:웹|서비스|플랫폼|API)\\s*(?:서버|개발)|(?:서버|백엔드)\\s*(?:개발|설계)|REST(?:ful)?|Spring|서버\\s*비즈니스|back[ -]?end|server[ -]?side"),
    excludedRoles = regex(NOT_BACKEND + "|로보틱스|robotics|컴퓨터\\s*비전|computer\\s*vision"),
)

// Toss는 Backend 공식 분류에서 쓰는 Server Developer 직함을 회사별로 허용한다.
private fun tossPolicy(companyId: String): JobPolicy = JobPolicy(
    titleRoles = regex(when (companyId) {
        "toss-place" -> "server\\s*(?:developer|engineer)|back[ -]?end|백엔드|서버\\s*개발"
        "toss-bank", "toss-securities", "toss-payments" -> "server\\s*(?:developer|engineer)|back[ -]?end\\s*(?:developer|engineer)?|백엔드|서버\\s*개발"
        else -> "server\\s*developer|back[ -]?end|백엔드|서버\\s*개발"
    }),
    bodyEvidence = regex("서버|백엔드|Spring|REST(?:ful)?|API|back[ -]?end|server[ -]?side|business\\s*logic"),
    excludedRoles = regex(NOT_BACKEND + "|database\\s*(?:engineer|administrator)|\\bdba\\b"),
)

// 카카오 계열사마다 직함이 달라 서버/백엔드를 먼저 확인한다. 게임 서버는 VX에서도 제외한다.
private fun kakaoPolicy(companyId: String): JobPolicy = JobPolicy(
    titleRoles = regex(when (companyId) {
        "kakaobank" -> "서버\\s*개발|백엔드|back[ -]?end|server\\s*(?:developer|engineer)"
        "kakaostyle" -> "백엔드|back[ -]?end|서버\\s*개발"
        "kakaopaysec", "kakaopay", "kakaopayins" -> "서버\\s*(?:개발|엔지니어)|백엔드|back[ -]?end|server\\s*(?:developer|engineer)"
        "dktechin", "kakaovx" -> "백엔드|back[ -]?end|(?:웹|서비스)\\s*서버\\s*개발|서버\\s*개발"
        else -> "백엔드|back[ -]?end|서버\\s*(?:개발|엔지니어)|server[ -]?(?:developer|engineer)|웹\\s*서비스\\s*개발"
    }),
    bodyEvidence = regex("(?:서버|백엔드)\\s*(?:개발|설계|운영)|Spring|REST(?:ful)?|API|back[ -]?end|server[ -]?side|비즈니스\\s*로직"),
    excludedRoles = regex(NOT_BACKEND + "|게임.*서버|언리얼|unreal|unity\\s*(?:developer|engineer)"),
)

// Coupang Technology 소속이라는 이유만으로 generic Software Engineer를 웹 백엔드로 간주하지 않는다.
private fun coupangPolicy(companyId: String): JobPolicy = JobPolicy(
    titleRoles = regex(when (companyId) {
        "coupang-pay", "coupang-financial" -> "back[ -]?end|백엔드|server[ -]?(?:side|developer|engineer)|서버\\s*개발"
        "coupang-play" -> "back[ -]?end|백엔드|server[ -]?side|서버\\s*개발"
        else -> "back[ -]?end|백엔드|server[ -]?(?:side|developer|engineer)|서버\\s*개발"
    }),
    bodyEvidence = regex("back[ -]?end|server[ -]?side|(?:web|backend)\\s*(?:service|application)|(?:REST|web)\\s*API|microservice|business\\s*logic|Spring|백엔드|서버\\s*개발"),
    excludedRoles = regex(NOT_BACKEND + "|infrastructure\\s*engineer|infra\\s*engineer|인프라\\s*엔지니어"),
)

private fun daangnPolicy(companyId: String): JobPolicy = JobPolicy(
    titleRoles = regex(when (companyId) {
        "karrot-canada", "karrot-japan" -> "back[ -]?end|server[ -]?(?:side|developer|engineer)|백엔드|サーバーサイド|バックエンド"
        else -> "back[ -]?end|백엔드|서버\\s*(?:개발|엔지니어)"
    }),
    bodyEvidence = regex("백엔드|back[ -]?end|(?:서버|서비스)\\s*개발|API|Spring|Rails|server[ -]?side|サーバー|バックエンド"),
    excludedRoles = regex(NOT_BACKEND),
)

private fun linePolicy(companyId: String): JobPolicy = JobPolicy(
    titleRoles = regex(when (companyId) {
        "ly-japan", "ly-communications" -> "バックエンド|サーバーサイド|Webアプリケーション.*(?:開発|エンジニア)|back[ -]?end|server[ -]?side"
        else -> "back[ -]?end|server[ -]?(?:side|developer|engineer)|백엔드|서버\\s*(?:개발|엔지니어)|バックエンド|サーバーサイド"
    }),
    bodyEvidence = regex("back[ -]?end|server[ -]?side|API|Spring|(?:서버|서비스)\\s*개발|バックエンド|サーバーサイド|Webアプリケーション|ウェブサービス"),
    excludedRoles = regex(NOT_BACKEND + "|game.*server|ゲーム.*サーバ"),
)

private fun woowaPolicy(companyId: String): JobPolicy = JobPolicy(
    titleRoles = regex(when (companyId) {
        "woowa-youths" -> "백엔드|back[ -]?end|서버\\s*개발|server\\s*(?:developer|engineer)"
        else -> "백엔드|back[ -]?end|서버\\s*(?:개발|엔지니어)|server\\s*(?:developer|engineer)"
    }),
    bodyEvidence = regex("백엔드|back[ -]?end|API|Spring|(?:서버|서비스)\\s*(?:개발|설계)|비즈니스\\s*로직|server[ -]?side"),
    excludedRoles = regex(NOT_BACKEND),
)

/** 다른 분야에도 사용할 수 있는 회사별 직함 사전. 소속 사이트가 제공한 직무 분류는 어댑터가 먼저 확인한다. */
private fun fieldPolicy(companyId: String, sourceId: String, categoryId: String): JobPolicy {
    val titles = when (sourceId) {
        "naver" -> naverFieldTitles(categoryId)
        "toss" -> tossFieldTitles(categoryId)
        "kakao" -> kakaoFieldTitles(companyId, categoryId)
        "coupang" -> coupangFieldTitles(categoryId)
        "daangn" -> daangnFieldTitles(companyId, categoryId)
        "line" -> lineFieldTitles(companyId, categoryId)
        "woowa" -> woowaFieldTitles(categoryId)
        "bucketplace" -> bucketplaceFieldTitles(categoryId)
        else -> error("회사별 분야 전략이 없습니다: $companyId / $categoryId")
    }
    val policy = when (categoryId) {
        "web-frontend" -> JobPolicy(regex(titles), regex(WEB_UI_WORK), regex(NON_DEVELOPMENT + "|" + FULLSTACK_ROLE + "|" + MOBILE_ROLE + "|백엔드|back[ -]?end"))
        "android" -> JobPolicy(regex(titles), regex(ANDROID_WORK), regex(NON_DEVELOPMENT + "|" + FULLSTACK_ROLE))
        "ios" -> JobPolicy(regex(titles), regex(IOS_WORK), regex(NON_DEVELOPMENT + "|" + FULLSTACK_ROLE))
        "fullstack" -> JobPolicy(regex(titles), regex(WEB_UI_WORK), regex(NON_DEVELOPMENT), regex(SERVER_WORK))
        "data-engineer" -> JobPolicy(regex(titles), regex(DATA_WORK), regex(NON_DEVELOPMENT + "|scientist|사이언티스트|analyst|분석가|데이터\\s*분석|データサイエンティスト|データアナリスト"))
        "ml-engineer" -> JobPolicy(regex(titles), regex(ML_WORK + "|" + MODEL_IMPLEMENTATION_WORK), regex(NON_DEVELOPMENT + "|research\\s*scientist|data\\s*scientist|리서치\\s*사이언티스트|데이터\\s*사이언티스트|researcher|연구원|リサーチサイエンティスト|データサイエンティスト|mlops|엠엘옵스|engineering\\s*manager|엔지니어링\\s*매니저"),
            if (companyId in setOf("kakaobank", "kakaopaysec")) regex(MODEL_IMPLEMENTATION_WORK) else null)
        "devops-sre" -> JobPolicy(regex(titles), regex(OPERATIONS_WORK), regex(NON_DEVELOPMENT + "|보안\\s*관제|security\\s*operations|help\\s*desk|헬프데스크|전산\\s*담당"))
        else -> error("분야 규칙이 없습니다: $categoryId")
    }
    return policy
}

private const val NON_DEVELOPMENT = "인재\\s*(?:풀|pool)|talent\\s*pool|\\bqa\\b|\\bsdet\\b|quality\\s*(?:engineer|assurance)|test\\s*engineer|테스트\\s*엔지니어|품질\\s*엔지니어|product\\s*manager|project\\s*manager|기획자|디자이너|\\bdesigner\\b|recruiter|채용\\s*담당|hardware|하드웨어|mechanical|기계\\s*설계"
private const val FULLSTACK_ROLE = "full[ -]?stack|풀\\s*스택|フルスタック|front[ -]?end\\s*(?:&|and|/|및)\\s*back[ -]?end|프론트엔드\\s*(?:및|/|&)\\s*백엔드"
private const val MOBILE_ROLE = "\\bandroid\\b|안드로이드|\\bios\\b|react\\s*native|리액트\\s*네이티브|모바일\\s*앱|mobile\\s*(?:app|engineer|developer)"
private const val WEB_UI_WORK = "React(?!\\s*Native)|Vue|Angular|Next\\.?js|Nuxt|HTML|CSS|DOM|웹\\s*(?:UI|프론트|애플리케이션|앱|서비스|화면)|브라우저|web\\s*(?:UI|application|frontend)|フロントエンド開発|Webアプリケーション|ブラウザ"
private const val ANDROID_WORK = "Kotlin|Jetpack|Compose|Android\\s*(?:SDK|앱|애플리케이션|アプリ)|안드로이드\\s*(?:앱|애플리케이션|개발)|Gradle|AOSP|モバイルアプリ"
private const val IOS_WORK = "Swift|SwiftUI|UIKit|Objective[ -]?C|Xcode|iOS\\s*(?:SDK|앱|애플리케이션|アプリ)|아이폰\\s*앱|iPhone\\s*app|Apple\\s*platform"
private const val SERVER_WORK = "REST(?:ful)?|GraphQL|API|Spring|Django|Rails|Node\\.?js|서버\\s*(?:개발|설계|비즈니스)|백엔드\\s*(?:개발|설계)|business\\s*logic|microservice|backend\\s*(?:service|development)|server[ -]?side|サーバーサイド|バックエンド開発"
private const val DATA_WORK = "ETL|ELT|데이터\\s*(?:파이프라인|수집|적재|처리|웨어하우스|레이크)|data\\s*(?:pipeline|ingestion|warehouse|lake)|Spark|Flink|Airflow|Kafka|データ(?:パイプライン|基盤|処理|収集)|データウェアハウス"
private const val ML_WORK = "모델\\s*(?:학습|서빙|추론|배포|구현)|(?:머신러닝|딥러닝)\\s*(?:모델|개발)|model\\s*(?:training|serving|inference|deployment)|(?:training|serving|deploying)\\s*(?:ML|machine\\s*learning|models)|PyTorch|TensorFlow|推論|機械学習モデル|モデル(?:学習|開発|実装|提供)"
private const val OPERATIONS_WORK = "CI\\s*/\\s*CD|CICD|배포\\s*(?:자동화|파이프라인)|인프라\\s*(?:자동화|코드)|서비스\\s*신뢰성|가용성|장애\\s*(?:대응|복구)|Terraform|Kubernetes|observability|reliability|incident\\s*(?:response|management)|infrastructure\\s*as\\s*code|SLO|SLI|オンコール|信頼性|可用性|構成管理|デプロイ"

private fun naverFieldTitles(category: String): String = when (category) {
    "web-frontend" -> "front[ -]?end|프론트\\s*엔드|웹\\s*UI\\s*개발"
    "android" -> "\\bandroid\\b|안드로이드"
    "ios" -> "\\bios\\b|아이오에스"
    "fullstack" -> FULLSTACK_ROLE
    "data-engineer" -> "data[ -]?(?:engineer|engineering)|데이터\\s*엔지니어|데이터\\s*(?:플랫폼|파이프라인)\\s*개발"
    "ml-engineer" -> "machine[ -]?learning\\s*(?:engineer|engineering)|\\bML\\s*(?:engineer|engineering)|머신러닝\\s*(?:엔지니어|개발)|딥러닝\\s*엔지니어|AI/ML\\s*(?:engineer|엔지니어)"
    "devops-sre" -> "dev[ -]?ops|\\bsre\\b|site\\s*reliability|사이트\\s*신뢰성\\s*엔지니어"
    else -> error("네이버 미지원 분야: $category")
}

private fun tossFieldTitles(category: String): String = when (category) {
    "web-frontend" -> "frontend\\s*(?:developer|engineer)|front[ -]?end|프론트엔드"
    "android" -> "\\bandroid\\b|안드로이드"
    "ios" -> "\\bios\\b|아이오에스"
    "fullstack" -> FULLSTACK_ROLE
    "data-engineer" -> "data\\s*(?:engineer|engineering)|데이터\\s*엔지니어"
    "ml-engineer" -> "machine\\s*learning\\s*(?:engineer|engineering)|\\bML\\s*(?:engineer|engineering)|머신러닝\\s*엔지니어"
    "devops-sre" -> "dev[ -]?ops|\\bsre\\b|site\\s*reliability"
    else -> error("토스 미지원 분야: $category")
}

private fun kakaoFieldTitles(companyId: String, category: String): String = when (category) {
    "web-frontend" -> "front[ -]?end|프론트\\s*엔드|웹\\s*프론트\\s*개발"
    "android" -> "\\bandroid\\b|안드로이드"
    "ios" -> "\\bios\\b|아이오에스"
    "fullstack" -> FULLSTACK_ROLE
    "data-engineer" -> "data[ -]?(?:engineer|engineering)|데이터\\s*엔지니어|데이터\\s*파이프라인\\s*개발"
    "ml-engineer" -> "machine[ -]?learning\\s*(?:engineer|engineering)|\\bML\\s*(?:engineer|engineering)|머신러닝\\s*(?:엔지니어|개발)|AI/ML\\s*(?:engineer|엔지니어)" +
        if (companyId in setOf("kakaobank", "kakaopaysec")) "|\\bAI\\s*engineer(?:ing)?|AI\\s*엔지니어(?:링)?" else ""
    "devops-sre" -> "dev[ -]?ops|\\bsre\\b|site\\s*reliability|사이트\\s*신뢰성"
    else -> error("카카오 미지원 분야: $category")
}

private fun coupangFieldTitles(category: String): String = when (category) {
    "web-frontend" -> "front[ -]?end|프론트엔드|web\\s*UI\\s*(?:developer|engineer)"
    "android" -> "\\bandroid\\b|안드로이드"
    "ios" -> "\\bios\\b|아이오에스"
    "fullstack" -> FULLSTACK_ROLE
    "data-engineer" -> "data[ -]?(?:engineer|engineering)|데이터\\s*엔지니어"
    "ml-engineer" -> "machine[ -]?learning\\s*(?:engineer|engineering)|\\bML\\s*(?:engineer|engineering)|머신러닝\\s*엔지니어"
    "devops-sre" -> "dev[ -]?ops|\\bsre\\b|site\\s*reliability"
    else -> error("쿠팡 미지원 분야: $category")
}

private fun daangnFieldTitles(companyId: String, category: String): String {
    val international = companyId in setOf("karrot-canada", "karrot-japan")
    return when (category) {
        "web-frontend" -> if (international) "front[ -]?end|フロントエンド|프론트엔드" else "front[ -]?end|프론트\\s*엔드"
        "android" -> "\\bandroid\\b|안드로이드"
        "ios" -> "\\bios\\b|아이오에스"
        "fullstack" -> FULLSTACK_ROLE
        "data-engineer" -> if (international) "data[ -]?(?:engineer|engineering)|データエンジニア" else "data[ -]?(?:engineer|engineering)|데이터\\s*엔지니어|software\\s*engineer\\s*,\\s*data\\b"
        "ml-engineer" -> if (international) "machine[ -]?learning\\s*engineer|\\bML\\s*engineer|機械学習エンジニア" else "machine[ -]?learning\\s*engineer|\\bML\\s*engineer|머신러닝\\s*엔지니어|software\\s*engineer\\s*,\\s*machine\\s*learning"
        "devops-sre" -> "dev[ -]?ops|\\bsre\\b|site\\s*reliability"
        else -> error("당근 미지원 분야: $category")
    }
}

private fun lineFieldTitles(companyId: String, category: String): String {
    val japan = companyId in setOf("ly-japan", "ly-communications")
    return when (category) {
        "web-frontend" -> if (japan) "フロントエンド|front[ -]?end" else "front[ -]?end|프론트엔드|フロントエンド"
        "android" -> "\\bandroid\\b|안드로이드"
        "ios" -> "\\bios\\b|아이오에스"
        "fullstack" -> FULLSTACK_ROLE
        "data-engineer" -> "data[ -]?(?:engineer|engineering)|데이터\\s*엔지니어|データエンジニア"
        "ml-engineer" -> "machine[ -]?learning\\s*(?:engineer|engineering)|\\bML\\s*engineer|머신러닝\\s*엔지니어|機械学習エンジニア"
        "devops-sre" -> "dev[ -]?ops|\\bsre\\b|site\\s*reliability|サイトリライアビリティ"
        else -> error("LINE 미지원 분야: $category")
    }
}

private fun woowaFieldTitles(category: String): String = when (category) {
    "web-frontend" -> "front[ -]?end|프론트\\s*엔드"
    "android" -> "\\bandroid\\b|안드로이드"
    "ios" -> "\\bios\\b|아이오에스"
    "fullstack" -> FULLSTACK_ROLE
    "data-engineer" -> "data[ -]?(?:engineer|engineering)|데이터\\s*엔지니어|데이터\\s*파이프라인\\s*개발"
    "ml-engineer" -> "machine[ -]?learning\\s*engineer|\\bML\\s*engineer|머신러닝\\s*(?:엔지니어|개발)"
    "devops-sre" -> "dev[ -]?ops|\\bsre\\b|site\\s*reliability|사이트\\s*신뢰성"
    else -> error("우아한형제들 미지원 분야: $category")
}

/** 오늘의집은 개발/비개발을 함께 제공한다. 기존 개발 전용 사이트의 지원 범위를 넓히지 않는다. */
private fun bucketplacePolicy(categoryId: String): JobPolicy = when (categoryId) {
    "web-backend" -> JobPolicy(
        regex("back[ -]?end|백엔드|서버\\s*(?:개발|엔지니어)|server[ -]?(?:developer|engineer)"),
        regex(SERVER_WORK), regex(NOT_BACKEND),
    )
    "web-frontend", "android", "ios", "fullstack", "data-engineer", "ml-engineer", "devops-sre" ->
        fieldPolicy("bucketplace", "bucketplace", categoryId)
    "planning" -> JobPolicy(
        regex("(?:서비스|사업|비즈니스)\\s*(?:전략\\s*)?기획|(?:service|business)\\s*planner"),
        regex("요구\\s*사항|서비스\\s*정책|사업\\s*(?:계획|전략|모델)|사업성|시장\\s*조사|서비스\\s*(?:기획|설계)|business\\s*(?:plan|strategy|model)|service\\s*(?:planning|design)|requirements"),
        regex(BUSINESS_ROLE_EXCLUDED + "|(?:상품|제품)\\s*기획\\s*디자이너|콘텐츠\\s*마케터"),
    )
    "product-management" -> JobPolicy(
        regex("(?:product|project)\\s*manager|product\\s*owner|프로덕트\\s*오너|\\bPO\\b|프로덕트\\s*매니저|프로젝트\\s*매니저|제품\\s*관리자|프로젝트\\s*관리자|\\bPM\\b"),
        regex("로드맵|roadmap|우선\\s*순위|prioriti[sz]|제품\\s*(?:전략|목표|관리)|프로젝트\\s*(?:관리|일정|목표)|일정\\s*관리|product\\s*(?:strategy|management)|project\\s*(?:management|planning)|stakeholder"),
        regex(BUSINESS_ROLE_EXCLUDED + "|engineering\\s*manager|엔지니어링\\s*매니저|개발\\s*팀장|program\\s*manager|프로그램\\s*매니저|product\\s*marketing\\s*manager"),
    )
    "design" -> JobPolicy(
        regex("디자이너|\\bdesigner\\b|\\bPB\\s*Design\\s*Lead\\b|(?:UX|UI|프로덕트|브랜드|그래픽|비주얼|콘텐츠)\\s*디자인"),
        regex("Figma|Adobe|Photoshop|Illustrator|프로토타입|prototype|디자인\\s*(?:시스템|가이드|제작|설계)|사용자\\s*경험|시각\\s*(?:디자인|커뮤니케이션)|user\\s*experience|visual\\s*design|brand\\s*(?:identity|design)"),
        regex(BUSINESS_ROLE_EXCLUDED + "|UX\\s*researcher|사용자\\s*리서처|design\\s*engineer|디자인\\s*엔지니어|설계\\s*엔지니어"),
    )
    "marketing" -> JobPolicy(
        regex("마케터|마케팅|\\bmarketer\\b|\\bmarketing\\b"),
        regex("캠페인|campaign|광고\\s*(?:집행|운영|성과)|퍼포먼스|콘텐츠\\s*(?:제작|기획)|브랜드\\s*(?:인지도|전략)|전환율|ROAS|CAC|CRM|리텐션|고객\\s*획득|customer\\s*acquisition|brand\\s*awareness"),
        regex(BUSINESS_ROLE_EXCLUDED + "|marketing\\s*(?:engineer|developer)|마케팅\\s*(?:엔지니어|개발자)|marketing\\s*analyst|마케팅\\s*분석가|\\bdesigner\\b|디자이너|디자인"),
    )
    "sales" -> JobPolicy(
        regex("영업|세일즈|\\bsales\\b|business\\s*development|사업\\s*개발|\\bBD\\b"),
        regex("신규\\s*(?:고객|파트너)|고객\\s*(?:발굴|확보|관리)|매출|영업\\s*(?:전략|목표|활동)|제휴\\s*(?:발굴|협상|계약)|파트너\\s*(?:발굴|협상)|수주|sales\\s*(?:pipeline|target|revenue)|lead\\s*generation|prospecting|negotiat|partnership|business\\s*development"),
        regex(BUSINESS_ROLE_EXCLUDED + "|sales\\s*(?:engineer|developer)|영업\\s*(?:개발자|엔지니어)|sales\\s*operations|영업\\s*운영|영업\\s*지원|sales\\s*support|영업\\s*기획|세일즈\\s*(?:엔지니어|개발자|운영|지원|기획)"),
    )
    else -> error("오늘의집 미지원 분야: $categoryId")
}

private const val BUSINESS_ROLE_EXCLUDED = "인재\\s*(?:풀|pool)|talent\\s*pool|채용\\s*담당|recruiter|\\bQA\\b|\\bSDET\\b|\\bSoftware\\s*Engineer\\b|소프트웨어\\s*엔지니어"

private fun bucketplaceFieldTitles(category: String): String = when (category) {
    "web-frontend" -> "front[ -]?end|프론트\\s*엔드|웹\\s*UI\\s*(?:개발|엔지니어)"
    "android" -> "\\bandroid\\b|안드로이드"
    "ios" -> "\\bios\\b|아이오에스"
    "fullstack" -> FULLSTACK_ROLE
    "data-engineer" -> "data[ -]?(?:engineer|engineering)|데이터\\s*엔지니어|데이터\\s*파이프라인\\s*개발"
    "ml-engineer" -> "machine[ -]?learning\\s*(?:engineer|engineering)|\\bML\\s*(?:engineer|engineering)|머신러닝\\s*(?:엔지니어|개발)"
    "devops-sre" -> "dev[ -]?ops|\\bsre\\b|site\\s*reliability"
    else -> error("오늘의집 미지원 개발 분야: $category")
}

// 두 카카오 계열사의 AI Engineer 직함은 모델 자체를 구현하는 근거가 있어야 한다.
// 외부 LLM API를 호출하거나 프롬프트를 작성하는 업무만으로 이 조건을 충족하지 않는다.
private const val MODEL_IMPLEMENTATION_WORK = "모델(?:을|의)?\\s*(?:학습|개발|구현|파인튜닝)|(?:모델\\s*)?(?:서빙|추론)\\s*(?:시스템|서버|엔진|파이프라인)\\s*(?:개발|구축|설계|최적화)|model\\s*(?:training|implementation|fine[ -]?tuning)|(?:train(?:ing)?|fine[ -]?tun(?:e|ing))\\s+(?:machine[ -]?learning\\s+|ML\\s+|AI\\s+)?models?|(?:build|develop|design)\\w*\\s+(?:model\\s+)?(?:serving|inference)\\s+(?:systems?|servers?|pipelines?)"

private val BUSINESS_CATEGORIES = setOf("planning", "product-management", "design", "marketing", "sales")

/** 비개발도 회사별 공식 직군을 선행 조건으로 삼고, 명확한 직함 + 본문 근거를 요구한다. */
private fun businessPolicy(sourceId: String, categoryId: String): JobPolicy {
    val base = bucketplacePolicy(categoryId)
    return when (sourceId) {
        "toss" -> when (categoryId) {
            "planning" -> base.copy(titleRoles = regex("(?:서비스|사업|비즈니스|커머스|수신상품|여신상품|여신제도|기업여신|IT)\\s*(?:전략\\s*)?(?:기획|심사기획)|전략\\s*매니저|(?:service|business|commerce|IT)\\s*(?:planning|planner)|strategy\\s*manager"))
            "sales" -> base.copy(titleRoles = regex("세일즈|영업|\\bsales\\b|business\\s*development|account\\s*manager"))
            else -> base
        }
        "daangn" -> when (categoryId) {
            "planning" -> base.copy(titleRoles = regex("(?:서비스|사업|비즈니스|광고)\\s*기획|(?:service|business)\\s*planner"))
            "sales" -> base.copy(titleRoles = regex("세일즈|영업|\\bsales\\b|business\\s*development|account\\s*(?:manager|executive)"))
            else -> base
        }
        else -> error("비개발 회사 전략이 없습니다: $sourceId")
    }
}
