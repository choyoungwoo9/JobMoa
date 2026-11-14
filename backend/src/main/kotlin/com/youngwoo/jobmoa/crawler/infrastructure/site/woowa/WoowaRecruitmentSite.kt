package com.youngwoo.jobmoa.crawler.infrastructure.site.woowa

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import org.springframework.stereotype.Component
import java.net.URI

/** 배민커리어와 우아한청년들의 별도 공식 포털을 모두 조회한다. */
@Component
class WoowaRecruitmentSite : RecruitmentSite {
    override val definition = SourceDefinition("woowa", "우아한형제들", "https://career.woowahan.com/recruitment")
    private val hosts = listOf("career.woowahan.com", "career.woowayouths.com")
    override val discoveryTargets = listOf(
        SourceDefinition("woowa-brothers", "우아한형제들", definition.listingUrl),
        SourceDefinition("woowa-youths", "우아한청년들", "https://career.woowayouths.com/recruitment"),
    )

    override fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) {
        discoveryTargets.forEach { discoverAt(browser, it.listingUrl, emit) }
    }

    override fun discoverAt(browser: BrowserPage, listingUrl: String, emit: (DiscoveredPosting) -> Unit) {
        require(discoveryTargets.any { it.listingUrl == listingUrl }) { "미지원 우아한형제들 계열사 목록 주소: $listingUrl" }
        discoverPortal(browser, URI(listingUrl).host, emit)
    }

    private fun discoverPortal(browser: BrowserPage, host: String, emit: (DiscoveredPosting) -> Unit) {
        var page = 0
        var received = 0
        val seen = mutableSetOf<String>()
        while (true) {
            // 요청은 0부터, 응답 pageNumber는 1부터 시작한다.
            browser.navigate("https://$host/w1/recruits?page=$page&size=100&sort=updateDate,desc&recruitCampaignSeq=0")
            val response = browser.evaluate("() => JSON.parse(document.body.innerText)").asObject("우아한형제들 목록")
            val data = response.successData()
            val rows = data["list"] as? List<*> ?: error("우아한형제들 공고 목록이 없습니다.")
            val total = (data["totalSize"] as? Number)?.toInt() ?: error("우아한형제들 전체 공고 수가 없습니다.")
            val pages = (data["totalPageNumber"] as? Number)?.toInt() ?: error("우아한형제들 페이지 수가 없습니다.")
            if (rows.isEmpty()) {
                check(received >= total) { "우아한형제들 전체 $total 건 중 $received 건만 조회했습니다." }
                return
            }
            var fresh = 0
            rows.forEach { value ->
                val job = value.asObject("우아한형제들 목록 항목")
                val id = job.string("recruitNumber")
                check(id.matches(Regex("[A-Za-z0-9_-]+"))) { "우아한형제들 공고 번호가 없습니다." }
                if (!seen.add(id)) return@forEach
                fresh++
                if (job["isHidden"] == true || job["recruitDeleteYn"] == true || job["isTemporaryStatus"] == true || job["isAfterOrEqualOpenDay"] == false || job.isClosed()) return@forEach
                val title = job.string("recruitName")
                check(title.isNotBlank()) { "우아한형제들 공고명이 없습니다: $id" }
                if (!WoowaDeveloperRule.accepts(host, job.jobGroup(), title)) return@forEach
                emit(DiscoveredPosting("https://$host/recruitment/$id/detail", job.company(), title))
            }
            // 마지막 페이지 판정 전에 검사해야 반복 페이지를 총건수 충족으로 오인하지 않는다.
            check(fresh > 0) { "우아한형제들 목록 페이지가 반복됐습니다: $host page=$page" }
            received += rows.size
            page++
            if (received >= total) {
                check(seen.size >= total) { "우아한형제들 목록에 중복이 있어 전체 공고를 확인하지 못했습니다: ${seen.size}/$total" }
                return
            }
            check(page < pages && page < 10_000) { "우아한형제들 목록이 반복되거나 일부만 조회됐습니다: $received/$total" }
        }
    }

    override fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        require(acceptsUrl(posting.url)) { "지원하지 않는 우아한형제들 공고 주소입니다." }
        val id = URI(posting.url).path.split('/')[2]
        val host = URI(posting.url).host
        val response = browser.evaluate(
            """async id => {
                const response = await fetch('/w1/recruits/' + encodeURIComponent(id), {cache:'no-store'});
                if (!response.ok) throw new Error('우아한형제들 상세 HTTP ' + response.status);
                return await response.json();
            }""", id
        ).asObject("우아한형제들 상세")
        val job = response.successData()
        check(job.string("recruitNumber") == id) { "우아한형제들 상세 공고 번호가 다릅니다." }
        val title = job.string("recruitName")
        check(title.isNotBlank()) { "우아한형제들 상세 공고명이 없습니다." }
        if (!WoowaDeveloperRule.accepts(host, job.jobGroup(), title)) throw PostingExcluded("우아한형제들·청년들 소프트웨어 직무 규칙에서 제외: $title")
        val youth = host == "career.woowayouths.com"
        val titleSelector = if (youth) ".recruit-detail-title .title" else ".recruitment-detail-title"
        val bodySelector = if (youth) ".recruit-detail .detail-view.editor-viewer" else ".recruitment-detail-body .editor-viewer"
        browser.waitFor(titleSelector)
        browser.waitFor(bodySelector)
        val renderedTitle = browser.text(titleSelector).orEmpty().trim()
        check(renderedTitle == title.trim()) { "우아한형제들 화면과 메타데이터 공고명이 다릅니다." }
        val content = browser.text(bodySelector).orEmpty().trim()
        check(content.length >= 100) { "우아한형제들 공고 본문을 충분히 읽지 못했습니다." }
        val subtitle = if (youth) "" else browser.text(".recruitment-detail-subtitle").orEmpty().trim()
        val summary = if (youth) "" else browser.text(".recruitment-detail-aside-summary").orEmpty().trim()
        val body = listOf(subtitle, content, summary).filter { it.isNotBlank() }.joinToString("\n\n")
        // 법인 주소(푸터)를 근무지로 잘못 사용하지 않는다. 본문에 명시된 값만 기록한다.
        val location = Regex("(?:근무지|근무장소|모집지역)\\s*[:：]\\s*([^\\n]+)").find(content)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { posting.location }
        return ExtractedPosting(title.trim(), job.company(), location, body, job.isClosed())
    }

    override fun acceptsUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host in hosts && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) && uri.path.matches(Regex("/recruitment/[A-Za-z0-9_-]+/detail/?")) && uri.rawQuery.isNullOrBlank()
    }.getOrDefault(false)
}

/** BA005001은 공식 Tech 직군. 보안·QA·DB 운영도 포함하므로 공고명에서 개발 역할을 추가로 확인한다. */
private object WoowaDeveloperRule {
    private val softwareTitle = Regex("""소프트웨어|백엔드|프론트엔드|풀스택|서버\s*개발|웹\s*개발|앱\s*개발|플랫폼\s*개발|플랫폼\s*엔지니어링|데이터\s*엔지니어(?:링)?|머신\s*러닝\s*(?:엔지니어|개발)|(?:AI|인공지능)\s*(?:엔지니어|개발)|안드로이드\s*(?:개발|엔지니어)|개발자|software|back.?end|front.?end|full.?stack|android|\bios\b|devops|\bsre\b|site reliability engineer|data engineer|machine learning engineer|\b(?:ml|ai) engineer\b|데브옵스""", RegexOption.IGNORE_CASE)
    private val excludedTitle = Regex("\\bqa\\b|\\btest(?:ing)?\\b|\\bquality\\b|테스트|사이언티스트|애널리스트|분석가|품질|정보보호|정보보안|침해|보안관제|데이터베이스|\\bdba\\b|scientist|analyst|데이터분석|프로덕트.?매니저|기획|디자인|영업", RegexOption.IGNORE_CASE)
    fun accepts(host: String, group: String, title: String): Boolean =
        // 청년들 BA005001은 재무 직군이다. 형제들의 Tech 코드를 다른 포털에 재사용하지 않는다.
        (host == "career.woowayouths.com" || group == "BA005001") &&
            softwareTitle.containsMatchIn(title) && !excludedTitle.containsMatchIn(title)
}

private fun Any?.asObject(label: String): Map<*, *> = this as? Map<*, *> ?: error("$label 응답 형식이 바뀌었습니다.")
private fun Map<*, *>.string(key: String): String = this[key]?.toString().orEmpty()
private fun Map<*, *>.successData(): Map<*, *> {
    check(string("code").toIntOrNull() in 2000..2999) { "우아한형제들 API 오류: ${this["code"]} ${this["message"]}" }
    return this["data"].asObject("우아한형제들 data")
}
private fun Map<*, *>.jobGroup(): String = (this["jobGroup"] as? Map<*, *>)?.get("recruitItemCode")?.toString().orEmpty()
private fun Map<*, *>.company(): String = when (val corporation = string("recruitCorporationNumber")) {
    "WOOWA_BROTHERS" -> "우아한형제들"
    "WOOWA_YOUTH", "WOOWA_YOUTHS" -> "우아한청년들"
    else -> corporation.ifBlank { error("우아한형제들 공고 법인 정보가 없습니다.") }
}
private fun Map<*, *>.isClosed(): Boolean = this["isAfterOrEqualEndDay"] == true ||
    ((this["recruitStatusCode"] as? Map<*, *>)?.get("text")?.toString() in setOf("마감", "종료", "채용마감"))
