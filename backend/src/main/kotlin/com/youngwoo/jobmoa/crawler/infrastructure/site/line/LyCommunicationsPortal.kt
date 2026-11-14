package com.youngwoo.jobmoa.crawler.infrastructure.site.line

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.BrowserPage
import java.net.URI

/** LY 공식 그룹회사 목록 → lycomm.co.jp → 공식 채용 포털로 확인한 별도 법인. */
internal object LyCommunicationsPortal {
    val definition = SourceDefinition("ly-communications", "LY Communications", "https://career.lycomm.co.jp/ja/position")
    private val softwareTitle = Regex("ソフトウェア|バックエンド|フロントエンド|サーバーサイド|ネイティブアプリ|プログラマー|開発エンジニア|Android|\\biOS\\b|software engineer|back.?end|front.?end|full.?stack|data engineer|データエンジニア", RegexOption.IGNORE_CASE)
    private val excludedTitle = Regex("テスト|品質|サポート|マネージャー|マネジメント|ディレクター|コンサル|管理職|開発PM|アナリスト|サイエンティスト|セキュリティ|運用|保守|\\bQA\\b|\\bPM\\b|\\bPL\\b|manager|scientist|analyst", RegexOption.IGNORE_CASE)

    fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) {
        browser.navigate(definition.listingUrl)
        browser.waitFor(".floating-menu-inner .number")
        val response = browser.evaluate("""() => ({
            total: document.querySelector('.floating-menu-inner .number')?.textContent.trim(),
            rows: Array.from(document.querySelectorAll('.job-data-list a.job-data')).map(a => ({
                url: a.href, title: a.querySelector('.name')?.textContent.trim()
            }))
        })""") as? Map<*, *> ?: error("LY Communications 목록 형식이 바뀌었습니다.")
        val total = Regex("\\d+").find(response["total"]?.toString().orEmpty())?.value?.toIntOrNull()
            ?: error("LY Communications 전체 공고 수가 없습니다.")
        val rows = response["rows"] as? List<*> ?: error("LY Communications 공고 목록이 없습니다.")
        check(rows.size == total) { "LY Communications 목록이 일부만 조회됐습니다: ${rows.size}/$total" }
        val seen = mutableSetOf<String>()
        rows.forEach { value ->
            val row = value as? Map<*, *> ?: error("LY Communications 목록 항목 형식이 바뀌었습니다.")
            val title = row["title"]?.toString().orEmpty()
            check(title.isNotBlank()) { "LY Communications 공고명이 없습니다." }
            if (!isDeveloper(title)) return@forEach
            val url = row["url"]?.toString() ?: error("LY Communications 공고 URL이 없습니다.")
            check(acceptsUrl(url)) { "미지원 LY Communications 상세 주소: $url" }
            if (seen.add(url)) emit(DiscoveredPosting(url, "LY Communications", title))
        }
    }

    fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        browser.waitFor(".un_jobDetail_ttl")
        browser.waitFor(".un_jobDetail_table .row")
        val title = browser.text(".un_jobDetail_ttl").orEmpty().trim()
        check(title.isNotBlank()) { "LY Communications 상세 공고명이 없습니다." }
        if (!isDeveloper(title)) throw PostingExcluded("LY Communications 소프트웨어 개발직 규칙에서 제외: $title")
        val body = browser.text(".un_jobDetail_table").orEmpty().trim()
        check(body.length >= 100) { "LY Communications 상세 본문을 충분히 읽지 못했습니다." }
        val location = browser.evaluate("""() => {
            const row = Array.from(document.querySelectorAll('.un_jobDetail_table .row')).find(r => r.querySelector('.title')?.textContent.trim() === '勤務地');
            return row?.querySelector('.description')?.innerText.trim() || '';
        }""")?.toString().orEmpty()
        val closed = Regex("(?:本求人|本ポジション|募集)(?:の募集|は|を)?(?:は)?終了(?:しました|しています)").containsMatchIn(body)
        return ExtractedPosting(title, "LY Communications", location.ifBlank { posting.location }, body, closed)
    }

    fun acceptsUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "career.lycomm.co.jp" && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) && uri.path.matches(Regex("/ja/position/[a-z]\\d+/?")) && uri.rawQuery.isNullOrBlank()
    }.getOrDefault(false)

    private fun isDeveloper(title: String): Boolean = softwareTitle.containsMatchIn(title) && !excludedTitle.containsMatchIn(title)
}
