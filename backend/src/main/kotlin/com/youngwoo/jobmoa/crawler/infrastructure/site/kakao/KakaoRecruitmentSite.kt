package com.youngwoo.jobmoa.crawler.infrastructure.site.kakao

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import org.springframework.stereotype.Component
import java.net.URI

@Component
class KakaoRecruitmentSite : RecruitmentSite {
    override val definition = SourceDefinition("kakao", "카카오", "https://careers.kakao.com/jobs")

    override val discoveryTargets: List<SourceDefinition> get() = listOf(definition) + KakaoAffiliatePortals.targets

    override fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) {
        discoveryTargets.forEach { discoverAt(browser, it.listingUrl, emit) }
    }

    override fun discoverAt(browser: BrowserPage, listingUrl: String, emit: (DiscoveredPosting) -> Unit) {
        val affiliate = KakaoAffiliatePortals.targets.find { it.listingUrl == listingUrl }
        if (affiliate != null) {
            KakaoAffiliatePortals.discover(browser, affiliate, emit)
            return
        }
        require(listingUrl == definition.listingUrl) { "미지원 카카오 목록 URL: $listingUrl" }
        discoverMain(browser, emit)
    }

    private fun discoverMain(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) {
        browser.navigate(definition.listingUrl)
        val seen = mutableSetOf<String>()
        var page = 1
        while (true) {
            val result = browser.evaluate("""async page => {
                const response = await fetch('/public/api/job-list?part=TECHNOLOGY&page=' + page);
                if (!response.ok) throw new Error('카카오 목록 HTTP ' + response.status);
                return await response.json();
            }""", page) as? Map<*, *> ?: error("카카오 목록 응답 형식 변경")
            val jobs = result["jobList"] as? List<*> ?: error("카카오 공고 목록 누락")
            val pages = (result["totalPage"] as? Number)?.toInt() ?: error("카카오 전체 페이지 수 누락")
            var fresh = 0
            jobs.forEach { raw ->
                val job = raw as? Map<*, *> ?: error("카카오 공고 형식 변경")
                val id = job["realId"]?.toString() ?: error("카카오 공고 ID 누락")
                if (!seen.add(id)) return@forEach
                fresh++
                if (job["closeFlag"] == true || job["privateFlag"] == true || !KakaoDeveloperRule.accepts(job)) return@forEach
                emit(DiscoveredPosting("https://careers.kakao.com/jobs/$id", job["companyName"]?.toString().orEmpty(),
                    job["jobOfferTitle"]?.toString().orEmpty(), job["locationName"]?.toString().orEmpty()))
            }
            if (page >= pages) return
            check(fresh > 0 && page < 10_000) { "카카오 목록 페이지가 반복되거나 비정상적입니다." }
            page++
        }
    }

    override fun closureReason(browser: BrowserPage, posting: DiscoveredPosting): String? = KakaoAffiliatePortals.closureReason(browser, posting)

    override fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        if (KakaoAffiliatePortals.acceptsUrl(posting.url)) return KakaoAffiliatePortals.extract(browser, posting)
        val id = URI(posting.url).path.substringAfterLast('/')
        val job = browser.evaluate("""async id => {
            const response = await fetch('/public/api/job-detail?id=' + encodeURIComponent(id) + '&isAvailable=true');
            if (!response.ok) throw new Error('카카오 상세 HTTP ' + response.status);
            return await response.json();
        }""", id) as? Map<*, *> ?: error("카카오 상세 응답 형식 변경")
        check(job["realId"]?.toString() == id) { "카카오 공고 ID가 일치하지 않습니다." }
        if (!KakaoDeveloperRule.accepts(job)) throw PostingExcluded("카카오 직무 규칙에서 제외: ${job["jobOfferTitle"]}")
        browser.waitFor(".cont_board.board_detail")
        val body = browser.text(".cont_board.board_detail").orEmpty().trim()
        check(body.length >= 120) { "카카오 본문 렌더링 실패" }
        return ExtractedPosting(job["jobOfferTitle"]?.toString().orEmpty(), job["companyName"]?.toString().orEmpty(),
            job["locationName"]?.toString().orEmpty(), body, job["closeFlag"] == true || job["statusCode"] == "CLOSED")
    }

    override fun prepareCapture(browser: BrowserPage, posting: DiscoveredPosting) {
        KakaoAffiliatePortals.prepareCapture(browser, posting)
    }

    override fun canonicalizeUrl(url: String): String = runCatching {
        val uri = URI(url)
        val legacyHosts = setOf("career.kakaostyle.com", "kakaovxrecruiter.ninehire.site")
        if (uri.scheme == "https" && uri.host in legacyHosts && Regex("/jobs/[A-Za-z0-9_-]+").matches(uri.path)) {
            url.replaceFirst("/jobs/", "/job_posting/")
        } else url
    }.getOrDefault(url)

    override fun acceptsUrl(url: String): Boolean = KakaoAffiliatePortals.acceptsUrl(url) || runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "careers.kakao.com" && Regex("/jobs/[A-Za-z]-[0-9]+").matches(uri.path)
    }.getOrDefault(false)
}

private object KakaoDeveloperRule {
    private val engineeringSkills = setOf("Android", "iOS", "Windows", "Web_front", "Server", "Hadoop_eco_system", "Algorithm_ML")
    private val excludedTitle = Regex("scientist|research|연구|analyst|분석가|(?<![A-Za-z])QA(?![A-Za-z])|quality|test engineer|보안|security|게임|game (?:server|client)|기획|디자인|운영.?담당|인재\\s*(?:풀|pool)|talent\\s*pool", RegexOption.IGNORE_CASE)
    private val softwareTitle = Regex("개발|developer|software|engineer|엔지니어|DevOps|SRE", RegexOption.IGNORE_CASE)
    fun accepts(job: Map<*, *>): Boolean {
        val title = job["jobOfferTitle"]?.toString().orEmpty()
        if (job["jobType"] != "TECHNOLOGY" || excludedTitle.containsMatchIn(title)) return false
        val skills = (job["skillSetList"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("skillSetType")?.toString() }.orEmpty()
        return skills.any { it in engineeringSkills } || softwareTitle.containsMatchIn(title)
    }
}
