package com.youngwoo.jobmoa.crawler.infrastructure.browser

import com.youngwoo.jobmoa.shared.CrawlerProperties
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/** 사이트별 활성 페이지 작업 수와 탐색 시작 간격을 각각 제한한다. */
@Component
class SiteLimiter(private val properties: CrawlerProperties) {
    private class Site(val slots: Semaphore) { var nextNavigation = 0L }
    private val sites = ConcurrentHashMap<String, Site>()
    private fun site(host: String) = sites.computeIfAbsent(host) {
        Site(Semaphore(properties.siteOverrides[host] ?: properties.siteConcurrency, true))
    }
    fun tryAcquire(host: String): AutoCloseable? {
        val slots = site(host).slots
        return if (slots.tryAcquire()) AutoCloseable { slots.release() } else null
    }
    fun beforeNavigation(host: String) {
        val site = site(host)
        val delay = synchronized(site) {
            val now = System.nanoTime()
            val at = maxOf(now, site.nextNavigation)
            site.nextNavigation = at + properties.minimumNavigationInterval.toNanos()
            at - now
        }
        if (delay > 0) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(delay)
    }
}
