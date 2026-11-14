package com.youngwoo.jobmoa.crawler.infrastructure.site.line

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Instant

/** LINE Careers의 전 지역·계열사와 일본 LY Corporation 공식 채용 포털을 조회한다. */
@Component
class LineRecruitmentSite : RecruitmentSite {
    override val definition = SourceDefinition("line", "LINE", "https://careers.linecorp.com/ko/jobs/")
    override val discoveryTargets = listOf(
        SourceDefinition("line-careers", "LINE Careers 전체 계열사·지역", definition.listingUrl),
        LyRecruitmentPortal.definition,
        LyCommunicationsPortal.definition,
    )

    override fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) {
        discoveryTargets.forEach { discoverAt(browser, it.listingUrl, emit) }
    }

    override fun discoverAt(browser: BrowserPage, listingUrl: String, emit: (DiscoveredPosting) -> Unit) {
        if (listingUrl == LyCommunicationsPortal.definition.listingUrl) {
            LyCommunicationsPortal.discover(browser, emit)
            return
        }
        if (listingUrl == LyRecruitmentPortal.definition.listingUrl) {
            LyRecruitmentPortal.discover(browser, emit)
            return
        }
        require(listingUrl == definition.listingUrl) { "미지원 LINE 목록 주소: $listingUrl" }
        // Gatsby가 목록에 사용하는 전체 데이터다. 화면의 지역 필터나 렌더링 개수에 의존하지 않는다.
        browser.navigate("https://careers.linecorp.com/page-data/ko/jobs/page-data.json")
        val response = browser.evaluate("() => JSON.parse(document.body.innerText)").asObject("LINE 목록")
        val jobs = response.objectAt("result").objectAt("data").objectAt("allStrapiJobs")
        val edges = jobs["edges"] as? List<*> ?: error("LINE 목록 edges가 없습니다.")
        val total = (jobs["totalCount"] as? Number)?.toInt() ?: error("LINE 전체 공고 수가 없습니다.")
        check(edges.size == total) { "LINE 목록 데이터가 잘렸습니다: ${edges.size}/$total" }
        val seen = mutableSetOf<String>()
        edges.forEach { value ->
            val job = value.asObject("LINE 목록 항목").objectAt("node")
            if (job["publish"] != true || job["is_public"] != true || job["is_filters_public"] != true) return@forEach
            if (job.isClosed() || job.instant("start_date")?.isAfter(Instant.now()) == true) return@forEach
            val title = job.string("title").ifBlank { job.string("title_en") }
            check(title.isNotBlank()) { "LINE 공고명이 없습니다." }
            if (!LineDeveloperRule.accepts(job.names("job_unit"), job.names("job_fields"), title)) return@forEach
            val id = job["strapiId"]?.toString()?.takeIf { it.matches(Regex("\\d+")) }
                ?: error("LINE 공고 ID가 없습니다.")
            if (!seen.add(id)) return@forEach
            val company = job.names("companies").joinToString(" · ")
            check(company.isNotBlank()) { "LINE 공고 기업이 없습니다: $id" }
            emit(DiscoveredPosting("https://careers.linecorp.com/ko/jobs/$id/", company, title, job.names("cities").joinToString(" · ")))
        }
    }

    override fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        require(acceptsUrl(posting.url)) { "지원하지 않는 LINE 공고 주소입니다." }
        if (LyCommunicationsPortal.acceptsUrl(posting.url)) return LyCommunicationsPortal.extract(browser, posting)
        if (LyRecruitmentPortal.acceptsUrl(posting.url)) return LyRecruitmentPortal.extract(browser, posting)
        val id = URI(posting.url).path.trimEnd('/').substringAfterLast('/')
        val job = browser.evaluate(
            """async id => {
                const response = await fetch('/page-data/ko/jobs/' + id + '/page-data.json', {cache:'no-store'});
                if (!response.ok) throw new Error('LINE 상세 데이터 HTTP ' + response.status);
                const data = await response.json();
                if (!data.result?.data?.strapiJobs) throw new Error('LINE 상세 메타데이터가 없습니다.');
                return data.result.data.strapiJobs;
            }""", id
        ).asObject("LINE 상세")
        val title = job.string("title").ifBlank { job.string("title_en") }
        check(title.isNotBlank()) { "LINE 상세 공고명이 없습니다." }
        if (!LineDeveloperRule.accepts(job.names("job_unit"), job.names("job_fields"), title)) {
            throw PostingExcluded("LINE Engineering·세부 직무 규칙에서 제외: $title")
        }
        browser.waitFor(".job_detail_header .title")
        browser.waitFor(".job_content_wrap .content_inner")
        val renderedTitle = browser.text(".job_detail_header .title").orEmpty().trim()
        check(renderedTitle == title.trim()) { "LINE 상세 화면과 메타데이터 공고명이 다릅니다." }
        val body = browser.text(".job_content_wrap .content_inner").orEmpty().trim()
        check(body.length >= 100) { "LINE 공고 본문을 충분히 읽지 못했습니다." }
        val company = job.names("companies").joinToString(" · ").ifBlank { posting.company }
        check(company.isNotBlank()) { "LINE 상세 기업명이 없습니다." }
        return ExtractedPosting(title.trim(), company, job.names("cities").joinToString(" · ").ifBlank { posting.location }, body, job.isClosed())
    }

    override fun acceptsUrl(url: String): Boolean = LyRecruitmentPortal.acceptsUrl(url) || LyCommunicationsPortal.acceptsUrl(url) || runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "careers.linecorp.com" && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) && uri.path.matches(Regex("/ko/jobs/\\d+/?")) && uri.rawQuery.isNullOrBlank()
    }.getOrDefault(false)
}

/** Engineering 전체가 개발직은 아니다. QA·보안 운영·관리직을 걸러낸 뒤 소프트웨어 세부 직무를 허용한다. */
private object LineDeveloperRule {
    private val softwareFields = setOf("Client-side", "Web Development", "Server-side", "Data Engineering", "(Not in use)Front-end", "(Not in use)Desktop/C++", "(Not in use)Game Engineering", "(not in use)Android", "(not in use)iOS", "(not in use)Blockchain")
    private val softwareTitle = Regex("software|back.?end|front.?end|full.?stack|android|\\bios\\b|server engineer|data engineer|devops|\\bsre\\b.*(?:dev|engineer)|(?:alpha|data) dev\\b|개발자|소프트웨어", RegexOption.IGNORE_CASE)
    private val excludedTitle = Regex("\\bqa\\b|\\bset\\b|\\btest(?:ing)?\\b|\\bquality\\b|scientist|analyst|product manager|project manager|engineering manager|team lead|technical partner|security professional|security operations|incident response|support engineer|테스트|품질|분석가|기획|디자이너", RegexOption.IGNORE_CASE)

    fun accepts(units: List<String>, fields: List<String>, title: String): Boolean =
        "Engineering" in units && !excludedTitle.containsMatchIn(title) &&
            (fields.any { it in softwareFields } || softwareTitle.containsMatchIn(title))
}

private fun Any?.asObject(label: String): Map<*, *> = this as? Map<*, *> ?: error("$label 응답 형식이 바뀌었습니다.")
private fun Map<*, *>.objectAt(key: String): Map<*, *> = this[key].asObject("LINE $key")
private fun Map<*, *>.string(key: String): String = this[key]?.toString().orEmpty()
private fun Map<*, *>.names(key: String): List<String> = (this[key] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("name")?.toString() }.orEmpty()
private fun Map<*, *>.instant(key: String): Instant? = this[key]?.toString()?.let { value ->
    runCatching { Instant.parse(value) }.getOrElse { error("LINE $key 날짜 형식이 바뀌었습니다: $value") }
}
private fun Map<*, *>.isClosed(): Boolean = this["publish"] == false ||
    (this["until_filled"] != true && instant("end_date")?.isBefore(Instant.now()) == true)
