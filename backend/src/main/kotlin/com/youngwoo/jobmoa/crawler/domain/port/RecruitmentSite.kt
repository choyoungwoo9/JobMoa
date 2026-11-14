package com.youngwoo.jobmoa.crawler.domain.port

import com.youngwoo.jobmoa.crawler.domain.model.*

/** Site adapters contain discovery/extraction rules, never worker ownership. */
interface RecruitmentSite {
    val definition: SourceDefinition
    val discoveryTargets: List<SourceDefinition> get() = listOf(definition)
    fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit)
    fun discoverAt(browser: BrowserPage, listingUrl: String, emit: (DiscoveredPosting) -> Unit) { discover(browser, emit) }
    fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting
    fun acceptsUrl(url: String): Boolean
}

/** Keeps Playwright types out of the domain and adapters on their owning thread. */
interface BrowserPage {
    fun navigate(url: String)
    fun text(selector: String): String?
    fun evaluate(script: String, argument: Any? = null): Any?
    fun waitFor(selector: String)
    fun capture(): ByteArray
}
