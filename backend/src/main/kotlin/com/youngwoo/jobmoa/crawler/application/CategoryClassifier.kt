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
    private val strategies: Map<Pair<String, String>, BackendPolicy> = companies.list()
        .filter { "web-backend" in it.supportedCategoryIds }
        .associate { company ->
            val policy = when (company.sourceId) {
                "naver" -> naverPolicy(company.id)
                "toss" -> tossPolicy(company.id)
                "kakao" -> kakaoPolicy(company.id)
                "coupang" -> coupangPolicy(company.id)
                "daangn" -> daangnPolicy(company.id)
                "line" -> linePolicy(company.id)
                "woowa" -> woowaPolicy(company.id)
                else -> error("웹 백엔드 회사 전략이 없습니다: ${company.id}")
            }
            (company.id to "web-backend") to policy
        }

    fun supports(companyId: String, categoryId: String): Boolean =
        companies.find(companyId)?.supportedCategoryIds?.contains(categoryId) == true &&
            (categoryId == "all" || strategies.containsKey(companyId to categoryId))

    /** 후보 단계에서 불명확한 공고의 상세 페이지와 캡처를 불필요하게 요청하지 않는다. */
    fun candidate(categoryId: String, companyId: String, posting: DiscoveredPosting): Boolean {
        if (!supports(companyId, categoryId)) return false
        if (categoryId == "all") return true
        return strategies.getValue(companyId to categoryId).candidate(posting.title)
    }

    /** 본문에 '개발'이 있다는 이유로 포함하지 않는다. 회사별 서버 직무 + 업무 근거가 필요하다. */
    fun accepts(categoryId: String, companyId: String, posting: ExtractedPosting): Boolean {
        if (!supports(companyId, categoryId)) return false
        if (categoryId == "all") return true
        return strategies.getValue(companyId to categoryId).accepts(posting)
    }
}

private data class BackendPolicy(
    val titleRoles: Regex,
    val bodyEvidence: Regex,
    val excludedRoles: Regex,
) {
    fun candidate(title: String): Boolean = titleRoles.containsMatchIn(title) && !excludedRoles.containsMatchIn(title)
    fun accepts(posting: ExtractedPosting): Boolean = candidate(posting.title) &&
        posting.bodyText.length >= 40 && bodyEvidence.containsMatchIn(posting.bodyText)
}

// 제외 대상은 분야 계약이다. 프론트엔드와 협업한다는 본문 문장으로 백엔드 공고를 제외하지 않는다.
private const val NOT_BACKEND = "full[ -]?stack|풀\\s*스택|front[ -]?end|프론트\\s*엔드|devops|dev[ -]?ops|\\bsre\\b|site reliability|data[ -]?(?:engineer|engineering)|데이터\\s*엔지니어|data\\s*platform|데이터\\s*플랫폼|machine[ -]?learning|머신러닝|\\bmlops\\b|\\bml engineer\\b|AI\\s*엔지니어|게임\\s*서버|game\\s*server|\\bqa\\b|\\bsdet\\b|test\\s*engineer|security\\s*engineer|보안\\s*엔지니어|network\\s*engineer|네트워크\\s*엔지니어|hardware|하드웨어|embedded|임베디드|firmware|펌웨어|(?:android|ios)\\s*(?:engineer|developer)|안드로이드|인재\\s*(?:풀|pool)|talent\\s*pool|バックエンド以外|フルスタック|フロントエンド|ゲームサーバー|データエンジニア|SREエンジニア"
private fun regex(expression: String) = Regex(expression, RegexOption.IGNORE_CASE)

// NAVER의 Back-end/Software Development 분류를 선행 조건으로 사용하고 서비스 서버 개발을 확인한다.
private fun naverPolicy(companyId: String): BackendPolicy = BackendPolicy(
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
private fun tossPolicy(companyId: String): BackendPolicy = BackendPolicy(
    titleRoles = regex(when (companyId) {
        "toss-place" -> "server\\s*(?:developer|engineer)|back[ -]?end|백엔드|서버\\s*개발"
        "toss-bank", "toss-securities", "toss-payments" -> "server\\s*(?:developer|engineer)|back[ -]?end\\s*(?:developer|engineer)?|백엔드|서버\\s*개발"
        else -> "server\\s*developer|back[ -]?end|백엔드|서버\\s*개발"
    }),
    bodyEvidence = regex("서버|백엔드|Spring|REST(?:ful)?|API|back[ -]?end|server[ -]?side|business\\s*logic"),
    excludedRoles = regex(NOT_BACKEND + "|database\\s*(?:engineer|administrator)|\\bdba\\b"),
)

// 카카오 계열사마다 직함이 달라 서버/백엔드를 먼저 확인한다. 게임 서버는 VX에서도 제외한다.
private fun kakaoPolicy(companyId: String): BackendPolicy = BackendPolicy(
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
private fun coupangPolicy(companyId: String): BackendPolicy = BackendPolicy(
    titleRoles = regex(when (companyId) {
        "coupang-pay", "coupang-financial" -> "back[ -]?end|백엔드|server[ -]?(?:side|developer|engineer)|서버\\s*개발"
        "coupang-play" -> "back[ -]?end|백엔드|server[ -]?side|서버\\s*개발"
        else -> "back[ -]?end|백엔드|server[ -]?(?:side|developer|engineer)|서버\\s*개발"
    }),
    bodyEvidence = regex("back[ -]?end|server[ -]?side|(?:web|backend)\\s*(?:service|application)|(?:REST|web)\\s*API|microservice|business\\s*logic|Spring|백엔드|서버\\s*개발"),
    excludedRoles = regex(NOT_BACKEND + "|infrastructure\\s*engineer|infra\\s*engineer|인프라\\s*엔지니어"),
)

private fun daangnPolicy(companyId: String): BackendPolicy = BackendPolicy(
    titleRoles = regex(when (companyId) {
        "karrot-canada", "karrot-japan" -> "back[ -]?end|server[ -]?(?:side|developer|engineer)|백엔드|サーバーサイド|バックエンド"
        else -> "back[ -]?end|백엔드|서버\\s*(?:개발|엔지니어)"
    }),
    bodyEvidence = regex("백엔드|back[ -]?end|(?:서버|서비스)\\s*개발|API|Spring|Rails|server[ -]?side|サーバー|バックエンド"),
    excludedRoles = regex(NOT_BACKEND),
)

private fun linePolicy(companyId: String): BackendPolicy = BackendPolicy(
    titleRoles = regex(when (companyId) {
        "ly-japan", "ly-communications" -> "バックエンド|サーバーサイド|Webアプリケーション.*(?:開発|エンジニア)|back[ -]?end|server[ -]?side"
        else -> "back[ -]?end|server[ -]?(?:side|developer|engineer)|백엔드|서버\\s*(?:개발|엔지니어)|バックエンド|サーバーサイド"
    }),
    bodyEvidence = regex("back[ -]?end|server[ -]?side|API|Spring|(?:서버|서비스)\\s*개발|バックエンド|サーバーサイド|Webアプリケーション|ウェブサービス"),
    excludedRoles = regex(NOT_BACKEND + "|game.*server|ゲーム.*サーバ"),
)

private fun woowaPolicy(companyId: String): BackendPolicy = BackendPolicy(
    titleRoles = regex(when (companyId) {
        "woowa-youths" -> "백엔드|back[ -]?end|서버\\s*개발|server\\s*(?:developer|engineer)"
        else -> "백엔드|back[ -]?end|서버\\s*(?:개발|엔지니어)|server\\s*(?:developer|engineer)"
    }),
    bodyEvidence = regex("백엔드|back[ -]?end|API|Spring|(?:서버|서비스)\\s*(?:개발|설계)|비즈니스\\s*로직|server[ -]?side"),
    excludedRoles = regex(NOT_BACKEND),
)
