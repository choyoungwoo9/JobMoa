package com.youngwoo.jobmoa.crawler.application

import com.youngwoo.jobmoa.crawler.domain.model.CategoryDefinition
import com.youngwoo.jobmoa.crawler.domain.model.CompanyDefinition
import org.springframework.stereotype.Component
import java.net.URI
import java.util.Locale

/** 지원 범위는 회사별로 선언한다. 모르는 계열사를 본사 공고로 임의 취급하지 않는다. */
@Component
class CompanyRegistry(sources: SourceRegistry) {
    val categories = listOf(
        CategoryDefinition("all", "전체 개발 직무", "기존 기업별 개발 직무 기준으로 수집"),
        CategoryDefinition("web-backend", "웹 백엔드", "웹·앱 서비스의 API와 서버 비즈니스 로직 개발"),
    )
    private val definitions = buildList {
        fun company(id: String, sourceId: String, portalId: String, name: String, vararg aliases: String, backend: Boolean = true) {
            val portal = sources.adapter(sourceId).discoveryTargets.find { it.id == portalId }
                ?: error("회사 $id 의 수집 포털이 없습니다: $portalId")
            add(CompanyDefinition(id, sourceId, name, portal.listingUrl, portalId,
                (aliases.toSet() + name), if (backend) setOf("all", "web-backend") else setOf("all")))
        }
        company("naver", "naver", "naver", "네이버", "NAVER", "NAVER Corporation", "네이버(주)")
        company("naver-cloud", "naver", "naver-cloud", "네이버클라우드", "NAVER Cloud", "NAVER CLOUD", "NAVER Cloud Corp.")
        company("snow", "naver", "naver-snow", "SNOW", "스노우", "SNOW Corporation")
        company("kream", "naver", "naver-snow", "KREAM", "크림", "KREAM Corporation")
        company("naver-labs", "naver", "naver-labs", "네이버랩스", "NAVER LABS", "NAVER LABS Corporation")
        company("naver-webtoon", "naver", "naver-webtoon", "네이버웹툰", "NAVER WEBTOON", "WEBTOON", "네이버웹툰(유)")
        company("naver-financial", "naver", "naver-financial", "네이버파이낸셜", "NAVER Financial", "NAVER FINANCIAL")
        company("naver-ins", "naver", "naver-ins", "네이버 I&S", "NAVER I&S", "네이버아이앤에스")
        company("naver-z", "naver", "naver-z", "네이버 Z", "NAVER Z", "네이버제트")

        company("toss", "toss", "toss", "토스", "비바리퍼블리카", "Viva Republica")
        company("toss-bank", "toss", "toss", "토스뱅크", "Toss Bank")
        company("toss-securities", "toss", "toss", "토스증권", "Toss Securities")
        company("toss-payments", "toss", "toss", "토스페이먼츠", "Toss Payments")
        company("toss-place", "toss", "toss", "토스플레이스", "Toss Place")
        company("toss-insurance", "toss", "toss", "토스인슈어런스", "Toss Insurance")
        company("toss-income", "toss", "toss", "토스인컴", "Toss Income")
        company("toss-mobile", "toss", "toss", "토스모바일", "Toss Mobile")
        company("toss-cx", "toss", "toss", "토스씨엑스", "Toss CX")

        company("kakao", "kakao", "kakao", "카카오", "Kakao", "Kakao Corp.")
        company("kakaopay", "kakao", "kakaopay", "카카오페이", "kakaopay")
        company("kakaobank", "kakao", "kakaobank", "카카오뱅크", "kakaobank")
        company("kakaopaysec", "kakao", "kakaopaysec", "카카오페이증권", "kakaopay securities")
        company("kakaomobility", "kakao", "kakaomobility", "카카오모빌리티", "kakao mobility")
        company("kakaoenterprise", "kakao", "kakaoenterprise", "카카오엔터프라이즈", "kakao enterprise")
        company("kakaoent", "kakao", "kakaoent", "카카오엔터테인먼트", "kakao entertainment")
        company("kakaostyle", "kakao", "kakaostyle", "카카오스타일", "kakao style")
        company("kakaovx", "kakao", "kakaovx", "카카오VX", "kakao vx")
        company("kakaopayins", "kakao", "kakaopayins", "카카오페이손해보험", "kakaopay insurance")
        company("dktechin", "kakao", "dktechin", "디케이테크인", "dktechin", "DK Techin")
        company("kidsnote", "kakao", "kidsnote", "키즈노트", "kidsnote")

        company("coupang", "coupang", "coupang", "쿠팡", "Coupang")
        company("coupang-pay", "coupang", "coupang", "쿠팡페이", "Coupang Pay")
        company("coupang-play", "coupang", "coupang", "쿠팡플레이", "Coupang Play")
        company("coupang-financial", "coupang", "coupang", "쿠팡파이낸셜", "Coupang Financial")
        company("coupang-eats", "coupang", "coupang", "쿠팡이츠", "Coupang Eats")
        company("coupang-eats-service", "coupang", "coupang", "쿠팡이츠서비스", "Coupang Eats Services")
        company("coupang-cfs", "coupang", "coupang", "쿠팡풀필먼트서비스", "CFS", "Coupang Fulfillment Services")
        company("coupang-cls", "coupang", "coupang", "쿠팡로지스틱스서비스", "CLS", "Coupang Logistics Services")
        company("coupang-cplb", "coupang", "coupang", "CPLB")
        company("coupang-japan", "coupang", "coupang", "CP One Japan (Rocket Now)")

        company("daangn", "daangn", "daangn", "당근", "당근마켓", "당근마켓(주)", "Karrot")
        company("daangn-pay", "daangn", "daangn", "당근페이", "Karrot Pay")
        company("daangn-service", "daangn", "daangn-service", "당근서비스")
        company("karrot-canada", "daangn", "karrot-north-america", "Karrot Canada", "Karrot Canada Corp.")
        company("karrot-japan", "daangn", "karrot-japan", "Karrot Japan", "Karrot Japan Corp.")

        company("line-plus", "line", "line-careers", "LINE Plus", "LINE Plus Corporation")
        company("line-pay-plus", "line", "line-careers", "LINE Pay Plus")
        company("line-taiwan", "line", "line-careers", "LINE Taiwan")
        company("line-pay-taiwan", "line", "line-careers", "LINE Pay Taiwan")
        company("line-vietnam", "line", "line-careers", "LINE VIETNAM")
        company("line-thailand", "line", "line-careers", "LINE Company Thailand")
        company("line-pay-thailand", "line", "line-careers", "LINE Pay (Thailand) Company Limited")
        company("line-pay-thailand-korea", "line", "line-careers", "LINE Pay (Thailand) Korea Office")
        company("line-indonesia", "line", "line-careers", "LINE Indonesia")
        company("line-financial", "line", "line-careers", "LINE Financial Corp.")
        company("line-studio", "line", "line-careers", "LINE studio", backend = false)
        company("line-ipx", "line", "line-careers", "IPX", backend = false)
        company("line-friends-square", "line", "line-careers", "LINE Friends Square", backend = false)
        company("ly-japan", "line", "ly-japan", "LY Corporation", "LINEヤフー株式会社", "LINEヤフー")
        company("ly-communications", "line", "ly-communications", "LY Communications", "LYコミュニケーションズ株式会社", "LYコミュニケーションズ")

        company("woowa-brothers", "woowa", "woowa-brothers", "우아한형제들", "Woowa Brothers")
        company("woowa-youths", "woowa", "woowa-youths", "우아한청년들", "Woowa Youths")
    }
    private val byId = definitions.associateBy { it.id }
    private val aliases = definitions.associate { it.id to it.companyNames.map(::normalize).toSet() }

    init { check(definitions.size == byId.size) { "중복 회사 ID가 있습니다." } }

    fun list(): List<CompanyDefinition> = definitions
    fun find(id: String): CompanyDefinition? = byId[id]
    fun require(id: String): CompanyDefinition = byId[id] ?: throw IllegalArgumentException("알 수 없는 회사: $id")

    fun matches(companyId: String, postingCompany: String, url: String): Boolean {
        val company = find(companyId) ?: return false
        val names = (listOf(postingCompany) + postingCompany.split(Regex("\\s+[·|]\\s+"))).map(::normalize)
        if (names.any { it in aliases.getValue(companyId) }) return true
        // 전용 도메인의 회사명 표기가 달라도 해당 전용 회사는 식별할 수 있다.
        // 통합 포털(NAVER 본사 포함)의 회사명 불일치는 URL만으로 흡수하지 않는다.
        val host = runCatching { URI(url).host }.getOrNull() ?: return false
        val portalHost = URI(company.listingUrl).host
        val sharedHost = definitions.count { URI(it.listingUrl).host == portalHost } > 1
        return !sharedHost && company.portalId !in setOf("naver", "kakao") && host == portalHost
    }

    fun resolve(sourceId: String, postingCompany: String, url: String): List<CompanyDefinition> =
        definitions.filter { it.sourceId == sourceId && matches(it.id, postingCompany, url) }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("주식회사|\\(주\\)|㈜|\\(유\\)|유한회사"), "")
        .replace(Regex("[\\s.,]"), "")
}
