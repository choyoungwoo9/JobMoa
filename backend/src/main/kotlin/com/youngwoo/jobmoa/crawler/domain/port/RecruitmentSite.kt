package com.youngwoo.jobmoa.crawler.domain.port

import com.youngwoo.jobmoa.crawler.domain.model.*

/** Site adapters contain discovery/extraction rules, never worker ownership. */
interface RecruitmentSite {
    val definition: SourceDefinition
    val requiresWindowedBrowser: Boolean get() = false
    /** 공식 상세 API로 본문을 확인한 뒤, 새 캡처가 필요할 때만 원문 페이지를 열 수 있다. */
    val extractBeforeDetailNavigation: Boolean get() = false
    val discoveryTargets: List<SourceDefinition> get() = listOf(definition)
    fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit)
    fun discoverAt(browser: BrowserPage, listingUrl: String, emit: (DiscoveredPosting) -> Unit) { discover(browser, emit) }
    fun closureReason(browser: BrowserPage, posting: DiscoveredPosting): String? = null
    fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting
    fun prepareCapture(browser: BrowserPage, posting: DiscoveredPosting) {}
    /** 캡처 시 API 본문과 실제 렌더링 화면을 대조할 때 어댑터가 선택적으로 사용한다. */
    fun prepareCapture(browser: BrowserPage, posting: DiscoveredPosting, expectedBodyText: String?) {
        prepareCapture(browser, posting)
    }
    fun canonicalizeUrl(url: String): String = url
    fun acceptsUrl(url: String): Boolean
}

/** Keeps Playwright types out of the domain and adapters on their owning thread. */
interface BrowserPage {
    fun navigate(url: String)
    /** 공고 탐색만 404·410의 화면을 확인할 수 있다. 403·429·5xx는 여전히 실패한다. */
    fun navigatePosting(url: String) { navigate(url) }
    fun responseStatus(): Int? = null
    fun currentUrl(): String? = null
    fun dialogMessages(): List<String> = emptyList()
    fun readJson(url: String): Any?
    fun text(selector: String): String?
    fun evaluate(script: String, argument: Any? = null): Any?
    fun waitFor(selector: String)
    fun capture(): ByteArray
}
