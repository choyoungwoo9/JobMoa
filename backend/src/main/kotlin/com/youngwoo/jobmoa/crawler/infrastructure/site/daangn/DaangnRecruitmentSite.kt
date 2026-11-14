package com.youngwoo.jobmoa.crawler.infrastructure.site.daangn

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime

/** 당근 채용 포털에 공개된 당근·당근페이 등 소속을 그대로 보존한다. */
@Component
class DaangnRecruitmentSite : RecruitmentSite {
    override val definition = SourceDefinition("daangn", "당근", "https://careers.daangn.com/jobs/")

    override val discoveryTargets = listOf(definition, DaangnServicePortal.definition) + KarrotGlobalPortals.definitions

    override fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) {
        discoverAt(browser, definition.listingUrl, emit)
        DaangnServicePortal.discover(browser, emit)
        KarrotGlobalPortals.definitions.forEach { KarrotGlobalPortals.discover(browser, it.listingUrl, emit) }
    }

    override fun discoverAt(browser: BrowserPage, listingUrl: String, emit: (DiscoveredPosting) -> Unit) {
        if (KarrotGlobalPortals.supportsListing(listingUrl)) {
            KarrotGlobalPortals.discover(browser, listingUrl, emit)
            return
        }
        if (listingUrl == DaangnServicePortal.definition.listingUrl) {
            DaangnServicePortal.discover(browser, emit)
            return
        }
        require(listingUrl == definition.listingUrl) { "지원하지 않는 당근 목록 주소: $listingUrl" }
        browser.navigate(definition.listingUrl)
        browser.waitFor("main")
        val result = browser.evaluate("""() => {
            const cards = Array.from(document.querySelectorAll('main a[href*="/jobs/role/"]')).filter(a => a.querySelector('h3'));
            const match = document.querySelector('main').innerText.match(/([\d,]+)개의 공고가 열려있어요/);
            return {total: match ? Number(match[1].replace(/,/g, '')) : null, cards: cards.map(a => ({
                url: a.href,
                title: a.querySelector('h3 > span.align-middle')?.textContent.trim() || a.querySelector('h3')?.textContent.trim(),
                company: a.querySelector('ul li')?.textContent.trim() || ''
            }))};
        }""") as? Map<*, *> ?: error("당근 목록 구조를 확인하지 못했습니다.")
        val total = (result["total"] as? Number)?.toInt() ?: error("당근 전체 공고 수를 확인하지 못했습니다.")
        val cards = (result["cards"] as? List<*>)?.map { it as? Map<*, *> ?: error("당근 공고 항목 형식 변경") }
            ?: error("당근 공고 목록을 찾지 못했습니다.")
        val unique = cards.distinctBy { it["url"] }
        // 현재 포털은 전 공고를 한 번에 렌더링한다. 페이지 방식 변경 시 일부만 성공 처리하지 않는다.
        check(unique.size == total) { "당근 전체 $total 건 중 ${unique.size} 건만 표시됐습니다. 목록 페이지 구조를 확인하세요." }
        unique.forEach { row ->
            val title = row["title"]?.toString().orEmpty()
            if (!DaangnDeveloperRule.accepts(title)) return@forEach
            val url = row["url"]?.toString().orEmpty()
            check(acceptsUrl(url)) { "미지원 당근 공고 주소: $url" }
            val company = row["company"]?.toString().orEmpty()
            check(company.isNotBlank()) { "당근 공고의 소속이 없습니다: $title" }
            emit(DiscoveredPosting(url, company, title))
        }
    }

    override fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        if (DaangnServicePortal.acceptsUrl(posting.url)) return DaangnServicePortal.extract(browser, posting)
        if (KarrotGlobalPortals.acceptsUrl(posting.url)) return KarrotGlobalPortals.extract(browser, posting)
        browser.waitFor("main article h1")
        browser.waitFor("main article .prose")
        val result = browser.evaluate("""() => {
            const flatten = value => Array.isArray(value) ? value.flatMap(flatten) : value && typeof value === 'object' ? [value, ...flatten(value['@graph'] || [])] : [];
            const job = Array.from(document.querySelectorAll('script[type="application/ld+json"]'))
                .flatMap(s => flatten(JSON.parse(s.textContent))).find(j => j['@type'] === 'JobPosting');
            const locations = job?.jobLocation ? (Array.isArray(job.jobLocation) ? job.jobLocation : [job.jobLocation]) : [];
            return {title: document.querySelector('main article h1').innerText.trim(),
                company: job?.hiringOrganization?.name || document.querySelector('main article header ul li')?.innerText.trim(),
                body: document.querySelector('main article .prose').innerText.trim(),
                location: locations.map(l => [l.address?.addressRegion, l.address?.addressLocality, l.address?.streetAddress].filter(Boolean).join(' ')).filter(Boolean).join(' / '),
                validThrough: job?.validThrough || '',
                closed: Array.from(document.querySelectorAll('main article button, main article a')).some(e => /^(채용이 마감되었습니다|마감된 공고|채용 마감|마감)$/.test(e.innerText.trim()))};
        }""") as? Map<*, *> ?: error("당근 상세 구조를 확인하지 못했습니다.")
        val title = result["title"]?.toString().orEmpty()
        if (!DaangnDeveloperRule.accepts(title)) throw PostingExcluded("당근 개발 직무명에 해당하지 않습니다: $title")
        val body = result["body"]?.toString().orEmpty()
        check(body.length >= 100) { "당근 공고 본문이 비어 있거나 너무 짧습니다: $title" }
        val company = result["company"]?.toString().orEmpty().ifBlank { posting.company }
        check(company.isNotBlank()) { "당근 공고 소속을 확인하지 못했습니다." }
        val deadline = result["validThrough"]?.toString().orEmpty().takeIf(String::isNotBlank)?.let {
            runCatching { Instant.parse(it) }.getOrElse { _ -> runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        }
        return ExtractedPosting(title, company, result["location"]?.toString().orEmpty().ifBlank { posting.location }, body,
            result["closed"] == true || deadline?.isBefore(Instant.now()) == true)
    }

    override fun acceptsUrl(url: String): Boolean = DaangnServicePortal.acceptsUrl(url) || KarrotGlobalPortals.acceptsUrl(url) || runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "careers.daangn.com" && Regex("/jobs/role/\\d+/?").matches(uri.path)
    }.getOrDefault(false)
}

/** 당근이 실제 사용하는 명확한 소프트웨어 직무명만 허용한다. Tech 전체를 포함하지 않는다. */
private object DaangnDeveloperRule {
    private val software = Regex("^(?:\\[[^]]+]\\s*)?(?:Software Engineer(?:\\b|,)|Site Reliability Engineer\\b)", RegexOption.IGNORE_CASE)
    fun accepts(title: String): Boolean = software.containsMatchIn(title)
}
