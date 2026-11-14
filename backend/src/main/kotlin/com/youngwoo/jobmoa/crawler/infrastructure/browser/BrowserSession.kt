package com.youngwoo.jobmoa.crawler.infrastructure.browser

import com.microsoft.playwright.*
import com.microsoft.playwright.options.*
import com.youngwoo.jobmoa.crawler.domain.model.PostingUrl
import com.youngwoo.jobmoa.crawler.domain.port.BrowserPage
import com.youngwoo.jobmoa.shared.CrawlerProperties

/** 생성·탐색·정리까지 작업자 한 스레드에서만 호출한다. */
class BrowserSession(private val properties: CrawlerProperties, private val limiter: SiteLimiter,
    private val manageSitePermit: Boolean = false) : AutoCloseable {
    private val owner = Thread.currentThread()
    private val playwright = Playwright.create(Playwright.CreateOptions().setEnv(System.getenv() + ("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD" to "1")))
    private val browser: Browser = try {
        playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(properties.headless)
            .also { if (properties.browserChannel.isNotBlank()) it.setChannel(properties.browserChannel) })
    } catch (error: Throwable) { playwright.close(); throw error }

    fun <T> withPage(block: (BrowserPage) -> T): T {
        checkOwner()
        var permit: AutoCloseable? = null
        try { return browser.newContext(Browser.NewContextOptions().setViewportSize(1440, 1000).setLocale("ko-KR").setTimezoneId("Asia/Seoul")).use { context ->
            val page = context.newPage()
            page.setDefaultTimeout(properties.navigationTimeout.toMillis().toDouble())
            block(object : BrowserPage {
                override fun navigate(url: String) {
                    checkOwner()
                    if (manageSitePermit) {
                        permit?.close(); permit = null
                        while (permit == null) { permit = limiter.tryAcquire(PostingUrl.siteKey(url)); if (permit == null) Thread.sleep(200) }
                    }
                    limiter.beforeNavigation(PostingUrl.siteKey(url))
                    val response = page.navigate(url, Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED))
                    check(response != null && response.status() in 200..399) { "페이지 응답 오류: ${response?.status()} $url" }
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
