package com.youngwoo.jobmoa.crawler.infrastructure.browser

import com.microsoft.playwright.*
import com.microsoft.playwright.options.*
import com.youngwoo.jobmoa.crawler.domain.model.PostingUrl
import com.youngwoo.jobmoa.crawler.domain.model.BrowserHttpFailure
import com.youngwoo.jobmoa.crawler.domain.port.BrowserPage
import com.youngwoo.jobmoa.shared.CrawlerProperties

/** 생성·탐색·정리까지 작업자 한 스레드에서만 호출한다. */
class BrowserSession(private val properties: CrawlerProperties, private val limiter: SiteLimiter,
    private val manageSitePermit: Boolean = false, private val publicOnly: Boolean = false) : AutoCloseable {
    private val owner = Thread.currentThread()
    private val playwright = Playwright.create(Playwright.CreateOptions().setEnv(System.getenv() + ("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD" to "1")))
    private val browser: Browser = try {
        playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(properties.headless)
            .also { if (properties.browserChannel.isNotBlank()) it.setChannel(properties.browserChannel) })
    } catch (error: Throwable) { playwright.close(); throw error }

    fun <T> withPage(block: (BrowserPage) -> T): T {
        checkOwner()
        var permit: AutoCloseable? = null
        try { return browser.newContext(Browser.NewContextOptions().setViewportSize(1440, 1000).setLocale("ko-KR").setTimezoneId("Asia/Seoul")
            .also { if (publicOnly) it.setServiceWorkers(ServiceWorkerPolicy.BLOCK) }).use { context ->
            if (publicOnly) context.routeWebSocket("**/*") { it.close() }
            val page = context.newPage()
            if (publicOnly) page.route("**/*") { route ->
                runCatching { PublicUrlGuard.validate(route.request().url()) }
                    .onSuccess { route.resume() }
                    .onFailure { route.abort() }
            }
            page.setDefaultTimeout(properties.navigationTimeout.toMillis().toDouble())
            var navigationSite: String? = null
            var status: Int? = null
            val dialogs = mutableListOf<String>()
            page.onDialog { dialog -> dialogs.add(dialog.message()); dialog.dismiss() }
            block(object : BrowserPage {
                override fun navigate(url: String) = navigateTo(url, false)
                override fun navigatePosting(url: String) = navigateTo(url, true)
                override fun responseStatus() = status
                override fun currentUrl(): String { checkOwner(); return page.url() }
                override fun dialogMessages(): List<String> { checkOwner(); return dialogs.toList() }
                private fun navigateTo(url: String, posting: Boolean) {
                    checkOwner()
                    status = null; dialogs.clear()
                    if (manageSitePermit) {
                        permit?.close(); permit = null
                        while (permit == null) { permit = limiter.tryAcquire(PostingUrl.siteKey(url)); if (permit == null) Thread.sleep(200) }
                    }
                    limiter.beforeNavigation(PostingUrl.siteKey(url))
                    navigationSite = PostingUrl.siteKey(url)
                    val response = page.navigate(url, Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED))
                    status = response?.status()
                    if (response == null || (response.status() !in 200..399 && !(posting && response.status() in setOf(404, 410))))
                        throw BrowserHttpFailure(response?.status() ?: 0, url)
                }
                override fun readJson(url: String): Any? {
                    checkOwner()
                    val site = PostingUrl.siteKey(url)
                    var requestPermit: AutoCloseable? = null
                    try {
                        // 현재 페이지와 다른 API 호스트도 별도 동시 요청 제한을 지킨다.
                        if (site != navigationSite) {
                            while (requestPermit == null) {
                                requestPermit = limiter.tryAcquire(site)
                                if (requestPermit == null) Thread.sleep(200)
                            }
                        }
                        limiter.beforeNavigation(site)
                        val response = context.request().get(url, RequestOptions.create().setTimeout(properties.navigationTimeout.toMillis().toDouble()))
                        try {
                            if (!response.ok()) throw BrowserHttpFailure(response.status(), response.url(), response.text().take(2000), "JSON")
                            return page.evaluate("body => JSON.parse(body)", response.text())
                        } finally { response.dispose() }
                    } finally { requestPermit?.close() }
                }
                override fun text(selector: String): String? { checkOwner(); return page.locator(selector).first().takeIf { it.count() > 0 }?.innerText()?.trim() }
                override fun evaluate(script: String, argument: Any?): Any? { checkOwner(); return page.evaluate(script, argument) }
                override fun waitFor(selector: String) { checkOwner(); page.locator(selector).first().waitFor(Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED)) }
                override fun capture(): ByteArray {
                    checkOwner()
                    page.evaluate("""async () => {
                        await Promise.race([document.fonts.ready, new Promise(r => setTimeout(r, 3000))]);
                        await Promise.race([Promise.all(Array.from(document.images).map(i => i.complete ? Promise.resolve() : new Promise(r => { i.onload = r; i.onerror = r; }))), new Promise(r => setTimeout(r, 3000))]);
                    }""")
                    return page.screenshot(Page.ScreenshotOptions().setFullPage(true).setAnimations(ScreenshotAnimations.DISABLED).setTimeout(properties.navigationTimeout.toMillis().toDouble()))
                }
            })
        } } finally { permit?.close() }
    }
    private fun checkOwner() { check(Thread.currentThread() === owner) { "Playwright는 생성한 작업자 스레드에서만 사용해야 합니다." } }
    override fun close() { checkOwner(); try { browser.close() } finally { playwright.close() } }
}
