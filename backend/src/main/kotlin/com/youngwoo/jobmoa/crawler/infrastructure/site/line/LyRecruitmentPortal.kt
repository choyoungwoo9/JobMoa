package com.youngwoo.jobmoa.crawler.infrastructure.site.line

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.BrowserPage
import java.net.URI

/** LINE Careers와 다른 일본 공식 포털. 일본어 엔지니어 직무 태그로 독립 판별한다. */
internal object LyRecruitmentPortal {
    val definition = SourceDefinition("ly-japan", "LY Corporation 일본", "https://www.lycorp.co.jp/ja/recruit/career/job-categories/")
    private val softwareRoles = setOf("j-0-0", "j-0-1", "j-0-2", "j-0-6", "j-0-7")
    private val excludedTitle = Regex("アナリスト|サイエンティスト|サポート|マネージャー|マネジメント|ディレクター|コンサル|コーチ|セキュリティ|品質|テスト|Staff Lead|Manager|Scientist|Analyst|\\bQA\\b|\\btest(?:ing)?\\b|\\bquality\\b", RegexOption.IGNORE_CASE)

    fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) {
        browser.navigate(definition.listingUrl)
        browser.waitFor("#js-result > li a")
        val data = browser.evaluate("""() => ({
            total: document.querySelector('#js-job-total')?.textContent.trim(),
            rows: Array.from(document.querySelectorAll('#js-result > li')).map(li => ({
                url: li.querySelector('a')?.href,
                title: li.querySelector('.title')?.textContent.trim(),
                category: li.querySelector('.category')?.textContent.trim(),
                roles: Array.from(li.querySelectorAll('.tag button[data-id^="j-"]')).map(b => b.dataset.id),
                locations: Array.from(li.querySelectorAll('.tag button[data-id^="l-"]')).map(b => b.textContent.trim())
            }))
        })""") as? Map<*, *> ?: error("LY 공식 목록 형식이 바뀌었습니다.")
        val rows = data["rows"] as? List<*> ?: error("LY 공식 공고 목록이 없습니다.")
        val total = data["total"]?.toString()?.replace(",", "")?.toIntOrNull() ?: error("LY 공식 전체 공고 수가 없습니다.")
        // 더보기는 DOM을 감추는 방식이다. 공식 총건수와 비교해 페이지네이션 변경/누락을 감지한다.
        check(rows.size == total) { "LY 전체 공고를 조회하지 못했습니다: ${rows.size}/$total" }
        val seen = mutableSetOf<String>()
        rows.forEach { value ->
            val row = value as? Map<*, *> ?: error("LY 목록 항목 형식이 바뀌었습니다.")
            val title = row["title"]?.toString().orEmpty()
            check(title.isNotBlank()) { "LY 공고명이 없습니다." }
            if (row["category"] != "エンジニアリング" || !acceptsRole(row["roles"], title)) return@forEach
            val url = row["url"]?.toString() ?: error("LY 공고 URL이 없습니다.")
            check(acceptsUrl(url)) { "미지원 LY 공고 주소: $url" }
            if (seen.add(url)) emit(DiscoveredPosting(url, "LY Corporation", title, row.strings("locations").joinToString(" · ")))
        }
    }

    fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        browser.waitFor("h1")
        browser.waitFor(".body-inner.search-target")
        val title = browser.text("h1").orEmpty().trim()
        check(title.isNotBlank()) { "LY 상세 공고명이 없습니다." }
        val metadata = browser.evaluate("""() => ({
            roles: Array.from(document.querySelectorAll('.c-tag-list a[data-id^="j-"]')).map(a => a.dataset.id),
            locations: Array.from(document.querySelectorAll('.c-tag-list a[data-id^="l-"]')).map(a => a.textContent.trim()),
            body: Array.from(document.querySelectorAll('.body-inner.search-target')).map(e => e.innerText.trim()).filter(Boolean).join('\n\n')
        })""") as? Map<*, *> ?: error("LY 상세 직무 정보를 확인하지 못했습니다.")
        if (!acceptsRole(metadata["roles"], title)) throw PostingExcluded("LY 일본 소프트웨어 직무 태그 규칙에서 제외: $title")
        val body = metadata["body"]?.toString().orEmpty().trim()
        check(body.length >= 100) { "LY 상세 본문을 충분히 읽지 못했습니다." }
        val closed = Regex("(?:本求人|本ポジション|募集)(?:の募集|は|を)?(?:は)?終了(?:しました|しています)").containsMatchIn(body)
        return ExtractedPosting(title, "LY Corporation", metadata.strings("locations").joinToString(" · ").ifBlank { posting.location }, body, closed)
    }

    fun acceptsUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "www.lycorp.co.jp" && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) && uri.path.matches(Regex("/ja/recruit/career/job-categories/(?:ly|lb)\\d+/?")) && uri.rawQuery.isNullOrBlank()
    }.getOrDefault(false)

    private fun acceptsRole(roles: Any?, title: String): Boolean =
        (roles as? List<*>)?.any { it?.toString() in softwareRoles } == true && !excludedTitle.containsMatchIn(title)
    private fun Map<*, *>.strings(key: String): List<String> = (this[key] as? List<*>)?.map { it.toString() }.orEmpty()
}
