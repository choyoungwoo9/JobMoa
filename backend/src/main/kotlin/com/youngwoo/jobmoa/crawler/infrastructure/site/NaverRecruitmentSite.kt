package com.youngwoo.jobmoa.crawler.infrastructure.site

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import org.springframework.stereotype.Component
import java.net.URI
import java.time.*
import java.time.format.DateTimeFormatter

/** NAVER Careers 통합 포털에 게시되는 계열사를 함께 다룬다. 판별 규칙도 이 수집기 소유다. */
@Component
class NaverRecruitmentSite : RecruitmentSite {
    override val definition = SourceDefinition("naver", "네이버", "https://recruit.navercorp.com/rcrt/list.do")
    override val discoveryTargets = listOf(definition,
        SourceDefinition("naver-cloud", "네이버클라우드", "https://recruit.navercloudcorp.com/rcrt/list.do"),
        SourceDefinition("naver-snow", "SNOW · KREAM", "https://recruit.snowcorp.com/rcrt/list.do"),
        SourceDefinition("naver-labs", "네이버랩스", "https://recruit.naverlabs.com/rcrt/list.do"),
        SourceDefinition("naver-webtoon", "네이버웹툰", "https://recruit.webtoonscorp.com/rcrt/list.do"),
        SourceDefinition("naver-financial", "네이버파이낸셜", "https://recruit.naverfincorp.com/rcrt/list.do"),
        SourceDefinition("naver-ins", "네이버 I&S", "https://recruit.naverins.com/rcrt/list.do"),
        SourceDefinition("naver-z", "네이버 Z", "https://recruit.naverz-corp.com/rcrt/list.do"),
    )
    private val hosts = discoveryTargets.map { URI(it.listingUrl).host }.toSet()
    private val sharedPortalHosts = hosts - setOf("recruit.naverz-corp.com", "recruit.naverfincorp.com")

    override fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) = discoverAt(browser, definition.listingUrl, emit)

    override fun discoverAt(browser: BrowserPage, listingUrl: String, emit: (DiscoveredPosting) -> Unit) {
        val host = URI(listingUrl).host
        require(host in hosts)
        // 계열사 전용 공고 ID는 통합 포털에서 열리지 않는다. 실제 통합 목록에 있는 ID만 합친다.
        val mainIds = if (host in sharedPortalHosts && host != URI(definition.listingUrl).host) {
            loadRows(browser, URI(definition.listingUrl).host).map { it["annoId"].toString() }.toSet()
        } else emptySet()
        loadRows(browser, host).forEach { row ->
            val id = row["annoId"]?.toString() ?: error("공고 ID가 없습니다.")
            val title = row["annoSubject"]?.toString().orEmpty()
            if (!NaverDeveloperRule.accepts(row["classCdNm"]?.toString().orEmpty(), row["subJobCdNm"]?.toString().orEmpty(), title)) return@forEach
            val url = row["jobDetailLink"]?.toString() ?: error("공고 URL이 없습니다: $id")
            check(acceptsUrl(url)) { "미지원 네이버 상세 주소: $url" }
            val canonical = if (id in mainIds) "https://recruit.navercorp.com/rcrt/view.do?annoId=$id" else url
            emit(DiscoveredPosting(canonical, row["sysCompanyCdNm"]?.toString().orEmpty(), title))
        }
    }

    private fun loadRows(browser: BrowserPage, host: String): List<Map<*, *>> {
        var offset = 0
        val seen = mutableSetOf<String>()
        val collected = mutableListOf<Map<*, *>>()
        while (true) {
            browser.navigate("https://$host/rcrt/loadJobList.do?firstIndex=$offset&recordCountPerPage=100&lang=ko")
            val response = browser.evaluate("() => JSON.parse(document.body.innerText)") as? Map<*, *> ?: error("네이버 목록 응답 형식이 바뀌었습니다.")
            val rows = response["list"] as? List<*> ?: error("네이버 공고 목록이 없습니다.")
            val total = response["totalSize"].toString().toIntOrNull() ?: error("네이버 전체 공고 수를 확인하지 못했습니다.")
            // 공식 목록 화면도 list=[] / totalSize=0을 '진행 중인 공고 없음'으로 표시한다.
            check(response["result"] == "Y" || (response["result"] == "N" && rows.isEmpty() && total == 0)) {
                "네이버 목록 조회 실패: ${response["result"]}"
            }
            if (rows.isEmpty()) {
                check(offset >= total) { "전체 $total 건 중 $offset 건만 조회됐습니다." }
                return collected
            }
            var fresh = 0
            rows.forEach { value ->
                val row = value as? Map<*, *> ?: error("네이버 공고 항목 형식이 바뀌었습니다.")
                val id = row["annoId"]?.toString() ?: error("공고 ID가 없습니다.")
                if (!seen.add(id)) return@forEach
                fresh++
                collected.add(row)
            }
            offset += rows.size
            if (offset >= total) return collected
            check(fresh > 0 && offset < 100_000) { "네이버 목록 페이지가 반복되거나 비정상적으로 많습니다." }
        }
    }

    override fun closureReason(browser: BrowserPage, posting: DiscoveredPosting): String? =
        if (acceptsUrl(posting.url) && browser.dialogMessages().any { it.trim() == "접수기간이 아닙니다." })
            "네이버 공고 상세의 '접수기간이 아닙니다.' 알림" else null

    override fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        if (NaverDeveloperRule.isTalentPool(posting.title)) throw PostingExcluded("네이버 인재풀 공고는 수집하지 않습니다.")
        browser.waitFor(".card_title")
        browser.waitFor(".detail_wrap")
        val title = browser.text(".card_title").orEmpty()
        val info = (browser.evaluate("() => Array.from(document.querySelectorAll('.card_info dd')).map(e => e.innerText.trim())") as? List<*>)?.map { it.toString() }.orEmpty()
        check(info.size >= 3) { "네이버 공고의 직군·직무 정보를 확인하지 못했습니다." }
        if (!NaverDeveloperRule.accepts(info[1], info[2], title)) throw PostingExcluded("네이버 직군·직무 규칙에서 제외: ${info[1]} / ${info[2]} / $title")
        val body = browser.text(".detail_wrap").orEmpty().replace('\uFEFF', ' ').trim()
        val location = Regex("근무(?:지|장소)\\s*[:：]\\s*([^\\n]+)").find(body)?.groupValues?.get(1)?.trim()
            ?: posting.location
        val deadlineText = info.lastOrNull().orEmpty()
        val deadline = Regex("~\\s*(\\d{4}\\.\\d{2}\\.\\d{2})\\s*\\((\\d{2}:\\d{2})\\)").find(deadlineText)?.let { match ->
            LocalDateTime.parse("${match.groupValues[1]} ${match.groupValues[2]}", DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
                .atZone(ZoneId.of("Asia/Seoul")).toInstant()
        }
        val explicitClosed = browser.text(".link_box")?.contains("마감") == true || deadline?.isBefore(Instant.now()) == true
        return ExtractedPosting(title, info[0].ifBlank { posting.company }, location, body, explicitClosed)
    }

    override fun acceptsUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host in hosts && uri.path == "/rcrt/view.do" &&
            Regex("(?:^|&)annoId=\\d+(?:&|$)").containsMatchIn(uri.rawQuery.orEmpty())
    }.getOrDefault(false)
}

private object NaverDeveloperRule {
    private val softwareRoles = setOf("Embedded SW", "Back-end", "Backend", "Front-end", "Frontend", "Android", "iOS", "Software Development", "DevOps", "SRE")
    // Tech 공통 직무로 등록된 경우도 명확한 개발 직함은 직무별 수집 단계까지 전달한다.
    private val softwareTitle = Regex("software engineer|firmware engineer|backend|back.end|frontend|front.end|full.?stack|server (developer|engineer)|(?:android|ios|data|machine learning|ml|ai|mlops|devops) (?:developer|engineer)|site reliability engineer|\\bSRE\\b|백엔드|프론트엔드|풀스택|(?:데이터|머신러닝|AI|ML|MLOps|DevOps)\\s*엔지니어|개발자|개발.?엔지니어", RegexOption.IGNORE_CASE)
    private val excluded = Regex("hardware|mechanical|보안|security|(?<![A-Za-z])QA(?![A-Za-z])|test engineer|scientist|research engineer|analyst|분석가|게임|game (?:server|client)|기획|디자이너|사업|sales|contents developer", RegexOption.IGNORE_CASE)
    private val talentPool = Regex("인재\\s*(?:풀|pool)|talent\\s*pool", RegexOption.IGNORE_CASE)
    fun isTalentPool(title: String): Boolean = talentPool.containsMatchIn(title)
    fun accepts(category: String, role: String, title: String): Boolean =
        category == "Tech" && !isTalentPool(title) && !excluded.containsMatchIn(title) && (role in softwareRoles || softwareTitle.containsMatchIn(title))
}
