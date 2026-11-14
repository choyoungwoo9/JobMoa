package com.youngwoo.jobmoa.crawler.infrastructure.site.daangn

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.BrowserPage
import java.net.URI
import java.time.Instant

/** 당근서비스는 그룹 포털 외에 별도 Greeting 게시판을 운영한다. */
internal object DaangnServicePortal {
    val definition = SourceDefinition("daangn-service", "당근서비스", "https://daangnservice.career.greetinghr.com/ko/apply")
    private val softwareRole = Regex("^(?:Software Engineer|Software Developer|Product Engineer\\s*\\(ERP\\)|Backend Engineer|Frontend Engineer|Full[ -]?stack Engineer|백엔드 개발자|프론트엔드 개발자)(?:\\b|\\s|$)", RegexOption.IGNORE_CASE)

    fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) {
        browser.navigate(definition.listingUrl)
        browser.waitFor("body")
        val response = browser.evaluate("""() => {
            const data = JSON.parse(document.querySelector('#__NEXT_DATA__').textContent).props.pageProps;
            const openings = data.dehydratedState?.queries?.find(q => q.queryKey.length === 1 && q.queryKey[0] === 'openings')?.state.data;
            return {openings, renderedCount: document.querySelectorAll('a[data-testid="공고_아이템"]').length};
        }""") as? Map<*, *> ?: error("당근서비스 목록 응답 형식 변경")
        val rows = response["openings"] as? List<*> ?: error("당근서비스 서버 렌더링 공고 목록이 없습니다.")
        check((response["renderedCount"] as? Number)?.toInt() == rows.size) { "당근서비스 공고 목록이 일부만 렌더링됐습니다." }
        val seen = mutableSetOf<String>()
        rows.forEach { value ->
            val row = value as? Map<*, *> ?: error("당근서비스 공고 항목 형식 변경")
            val id = row["openingId"]?.toString() ?: error("당근서비스 공고 ID 누락")
            check(seen.add(id)) { "당근서비스 목록 공고 ID 중복: $id" }
            if (row["deploy"] != true) return@forEach
            val title = row["title"]?.toString().orEmpty()
            if (!softwareRole.containsMatchIn(title)) return@forEach
            val positions = (row["openingJobPosition"] as? Map<*, *>)?.get("openingJobPositions") as? List<*>
            val location = locations(positions.orEmpty())
            emit(DiscoveredPosting("https://daangnservice.career.greetinghr.com/ko/o/$id", "당근서비스", title, location))
        }
    }

    fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        browser.waitFor("body")
        val result = browser.evaluate("""() => {
            const data = JSON.parse(document.querySelector('#__NEXT_DATA__').textContent).props.pageProps;
            const response = data.dehydratedState?.queries?.find(q => q.queryKey.includes('getOpeningById'))?.state.data;
            const editor = Array.from(document.querySelectorAll('.ql-editor')).find(e => e.getBoundingClientRect().width > 0);
            return {success: response?.success, data: response?.data, body: editor?.innerText.trim() || ''};
        }""") as? Map<*, *> ?: error("당근서비스 상세 응답 형식 변경")
        check(result["success"] == true) { "당근서비스 상세 공고 조회 실패" }
        val data = result["data"] as? Map<*, *> ?: error("당근서비스 상세 정보 누락")
        val info = data["openingsInfo"] as? Map<*, *> ?: error("당근서비스 공고 내용 누락")
        val title = info["title"]?.toString().orEmpty()
        if (!softwareRole.containsMatchIn(title)) throw PostingExcluded("당근서비스 소프트웨어 직무명에 해당하지 않습니다: $title")
        val body = result["body"]?.toString().orEmpty()
        check(body.length >= 100) { "당근서비스 공고 본문이 정상 렌더링되지 않았습니다: $title" }
        val positions = (data["jobPositionSetting"] as? Map<*, *>)?.get("jobPositions") as? List<*>
        val due = info["dueDate"]?.toString()?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return ExtractedPosting(title, "당근서비스", locations(positions.orEmpty()).ifBlank { posting.location }, body,
            info["status"] in setOf("CLOSED", "CLOSE", "EXPIRED") || due?.isBefore(Instant.now()) == true)
    }

    fun acceptsUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "daangnservice.career.greetinghr.com" && Regex("/ko/o/\\d+/?").matches(uri.path)
    }.getOrDefault(false)

    private fun locations(positions: List<*>): String = positions.mapNotNull {
        val place = (it as? Map<*, *>)?.get("workspacePlace") as? Map<*, *> ?: return@mapNotNull null
        listOf("place", "detailPlace").mapNotNull { key -> place[key]?.toString()?.takeIf(String::isNotBlank) }.joinToString(" ")
    }.filter(String::isNotBlank).distinct().joinToString(" / ")
}
