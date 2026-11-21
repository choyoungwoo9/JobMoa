package com.youngwoo.jobmoa.crawler.infrastructure.site

import com.youngwoo.jobmoa.crawler.domain.port.BrowserPage
import java.net.URI

/** Greeting의 실제 공고 URL에서 확인한 오류 컴포넌트만 해석한다. 단순 HTTP 404는 증거가 아니다. */
internal object GreetingClosure {
    fun reason(browser: BrowserPage, url: String): String? {
        val expected = URI(url)
        if (!Regex("/(?:ko/)?o/[1-9]\\d*/?").matches(expected.path) || browser.responseStatus() != 404) return null
        val current = browser.currentUrl()?.let(::URI) ?: return null
        if (current.host != expected.host || current.path.trimEnd('/') != expected.path.trimEnd('/')) return null
        val missing = browser.evaluate("""() =>
            [...document.querySelectorAll('[data-variant="title-01"]')].some(e => e.innerText.trim() === '페이지를 찾을 수 없습니다.') &&
            [...document.querySelectorAll('[data-variant="body-01"]')].some(e => e.innerText.replace(/\s+/g, ' ').trim() ===
                '해당 페이지에 대한 접근 권한이 없거나 페이지가 존재하지 않습니다. URL 주소를 다시 한번 확인해 주세요.')""") == true
        return if (missing) "Greeting 공고 상세 HTTP 404 + 공고 없음/공개 접근 종료 컴포넌트" else null
    }
}
