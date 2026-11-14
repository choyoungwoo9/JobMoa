package com.youngwoo.jobmoa.crawler.infrastructure.site.daangn

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.BrowserPage
import java.net.URI
import java.time.Instant

/** 한국 서비스의 국가 선택 → 각 국가 공식 사이트 → Careers 링크로 확인한 해외 포털. */
internal object KarrotGlobalPortals {
    val definitions = listOf(
        SourceDefinition("karrot-north-america", "Karrot 북미", "https://careers.ca.karrotmarket.com/jobs/"),
        SourceDefinition("karrot-japan", "Karrot Japan", "https://careers.jp.karrotmarket.com/jobs/"),
    )
    private val hosts = definitions.map { URI(it.listingUrl).host }.toSet()
    private val software = Regex("""software (?:engineer|developer)|back[ -]?end (?:engineer|developer)|front[ -]?end (?:engineer|developer)|full[ -]?stack (?:engineer|developer)|(?:android|ios|mobile|machine learning|ml|ai|data|devops|site reliability) (?:engineer|developer)|SREエンジニア|\bSRE\b|ソフトウェアエンジニア|バックエンドエンジニア|フロントエンドエンジニア|フルスタックエンジニア|データエンジニア|機械学習エンジニア|DevOpsエンジニア""", RegexOption.IGNORE_CASE)
    private val excluded = Regex("\\b(?:QA|SDET|security|hardware|mechanical|scientist|analyst|manager|director|sales)\\b|テスト|セキュリティ|サイエンティスト|アナリスト", RegexOption.IGNORE_CASE)
    private fun isDeveloper(title: String) = software.containsMatchIn(title) && !excluded.containsMatchIn(title)

    fun supportsListing(url: String) = definitions.any { it.listingUrl == url }

    fun discover(browser: BrowserPage, listingUrl: String, emit: (DiscoveredPosting) -> Unit) {
        require(supportsListing(listingUrl))
        browser.navigate(listingUrl)
        browser.waitFor("main")
        val response = browser.evaluate("""async () => {
            const response = await fetch('/page-data/jobs/page-data.json');
            if (!response.ok) throw new Error('Karrot 해외 목록 응답 오류: ' + response.status);
            const page = await response.json();
            const all = page.result?.data?.allJobPost;
            return {total: all?.totalCount, nodes: all?.nodes};
        }""") as? Map<*, *> ?: error("Karrot 해외 목록 응답 형식 변경")
        val total = (response["total"] as? Number)?.toInt() ?: error("Karrot 해외 전체 공고 수가 없습니다.")
        val nodes = response["nodes"] as? List<*> ?: error("Karrot 해외 공고 목록이 없습니다.")
        check(nodes.size == total) { "Karrot 해외 전체 $total 건 중 ${nodes.size} 건만 조회됐습니다." }
        val candidates = nodes.map { it as? Map<*, *> ?: error("Karrot 해외 공고 항목 형식 변경") }
            .filter { isDeveloper(it["title"]?.toString().orEmpty()) }
        if (candidates.isEmpty()) return

        // 동일 Greenhouse 공고가 한국·해외에 함께 게시되면 실제 한국 목록에 존재하는 ID만 한국 URL로 통일한다.
        browser.navigate("https://careers.daangn.com/jobs/")
        browser.waitFor("main")
        val domestic = (browser.evaluate("""() => Array.from(document.querySelectorAll('main a[href*="/jobs/role/"]'))
            .filter(a => a.querySelector('h3')).map(a => ({url: a.href, id: a.href.match(/\/jobs\/role\/(\d+)/)?.[1]}))""") as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }?.associate { it["id"].toString() to it["url"].toString() }
            ?: error("Karrot 해외·한국 중복 공고 확인 실패")
        val company = if (URI(listingUrl).host == "careers.jp.karrotmarket.com") "Karrot Japan Corp." else "Karrot Canada Corp."
        val seen = mutableSetOf<String>()
        candidates.forEach { node ->
            val title = node["title"]?.toString().orEmpty()
            val id = node["ghId"]?.toString() ?: error("Karrot 해외 공고의 Greenhouse ID가 없습니다: $title")
            check(Regex("\\d+").matches(id)) { "Karrot 해외 공고 ID 형식 변경: $id" }
            val external = node["externalUrl"]?.toString().orEmpty()
            val rawUrl = domestic[id] ?: external.ifBlank { URI(listingUrl).resolve("$id/").toString() }
            val uri = URI(rawUrl)
            check(uri.scheme == "https" && (acceptsUrl(rawUrl) || isDomesticUrl(rawUrl))) { "미지원 Karrot 해외 원문 주소: $rawUrl" }
            val canonical = URI(uri.scheme, uri.authority, uri.path, null, null).toString()
            if (seen.add(canonical)) emit(DiscoveredPosting(canonical,
                if (isDomesticUrl(canonical)) if (node["corporate"] == "KARROT_PAY") "당근페이" else "당근" else company,
                title, node["location"]?.toString().orEmpty()))
        }
    }

    fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        browser.waitFor("main")
        val result = browser.evaluate("""() => {
            const flatten = value => Array.isArray(value) ? value.flatMap(flatten) : value && typeof value === 'object' ? [value, ...flatten(value['@graph'] || [])] : [];
            const job = Array.from(document.querySelectorAll('script[type="application/ld+json"]')).flatMap(s => flatten(JSON.parse(s.textContent))).find(j => j['@type'] === 'JobPosting');
            if (!job) throw new Error('Karrot 해외 JobPosting 상세 정보를 확인하지 못했습니다.');
            const content = new DOMParser().parseFromString(job.description || '', 'text/html').body;
            content.querySelectorAll('p,div,li,h1,h2,h3,h4,br').forEach(e => e.append(document.createTextNode('\n')));
            const body = content.textContent.trim();
            const normalize = text => text.replace(/\s+/g, ' ').trim();
            const visible = normalize(document.querySelector('main').innerText);
            const locations = Array.isArray(job.jobLocation) ? job.jobLocation : [job.jobLocation];
            return {title: job.title || '', company: job.hiringOrganization?.name || '', body,
                rendered: visible.includes(normalize(job.title || '')) && visible.includes(normalize(body).slice(0, 100)),
                location: locations.filter(Boolean).map(l => [l.address?.addressRegion, l.address?.addressLocality, l.address?.streetAddress].filter(Boolean).join(' ')).filter(Boolean).join(' / '),
                validThrough: job.validThrough || ''};
        }""") as? Map<*, *> ?: error("Karrot 해외 상세 응답 형식 변경")
        val title = result["title"]?.toString().orEmpty()
        if (!isDeveloper(title)) throw PostingExcluded("Karrot 해외 소프트웨어 직무명에 해당하지 않습니다: $title")
        val body = result["body"]?.toString().orEmpty()
        check(result["rendered"] == true && body.length >= 100) { "Karrot 해외 공고 본문이 정상 렌더링되지 않았습니다: $title" }
        val deadline = result["validThrough"]?.toString()?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return ExtractedPosting(title, result["company"]?.toString().orEmpty().ifBlank { posting.company },
            result["location"]?.toString().orEmpty().ifBlank { posting.location }, body, deadline?.isBefore(Instant.now()) == true)
    }

    fun acceptsUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host in hosts && Regex("/jobs/\\d+/?").matches(uri.path)
    }.getOrDefault(false)

    private fun isDomesticUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "careers.daangn.com" && Regex("/jobs/role/\\d+/?").matches(uri.path)
    }.getOrDefault(false)
}
