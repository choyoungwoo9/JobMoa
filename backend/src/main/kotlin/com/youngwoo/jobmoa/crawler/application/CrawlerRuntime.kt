package com.youngwoo.jobmoa.crawler.application

import com.youngwoo.jobmoa.collection.application.*
import com.youngwoo.jobmoa.collection.domain.model.*
import com.youngwoo.jobmoa.collection.domain.repository.*
import com.youngwoo.jobmoa.applicationrequirements.application.ApplicationRequirementService
import com.youngwoo.jobmoa.applicationrequirements.domain.PublicPageFetchLease
import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.BrowserPage
import com.youngwoo.jobmoa.crawler.infrastructure.browser.*
import com.youngwoo.jobmoa.crawler.infrastructure.storage.CaptureStorage
import com.youngwoo.jobmoa.shared.*
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "jobmoa.crawler", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class CrawlerRuntime(private val properties: CrawlerProperties, private val tasks: CrawlTaskRepository,
    private val runs: CollectionRepository, private val collections: CollectionService,
    private val taskService: TaskService, private val sources: SourceRegistry,
    private val limiter: SiteLimiter, private val captures: CaptureStorage, private val classifier: CategoryClassifier,
    private val applicationRequirements: ApplicationRequirementService) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sequence = AtomicInteger()
    private val workers = Executors.newFixedThreadPool(properties.workers) { r -> Thread(r, "crawl-worker-${sequence.incrementAndGet()}") }
    private val applicationPageWorkers = Executors.newFixedThreadPool(properties.applicationPageWorkers) { r -> Thread(r, "application-page-worker-${sequence.incrementAndGet()}") }
    private val discovery = Executors.newSingleThreadExecutor { r -> Thread(r, "crawl-discovery") }
    private val owned = ConcurrentHashMap<String, String>()
    private val ownedPublicFetches = ConcurrentHashMap<String, String>()
    @Volatile private var running = false
    @Volatile private var discovering: String? = null

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        runs.recoverDiscovery()
        tasks.recoverExpired(Instant.now())
        running = true
        repeat(properties.workers) { workers.submit { workerLoop() } }
        repeat(properties.applicationPageWorkers) { applicationPageWorkers.submit { applicationPageWorkerLoop() } }
        discovery.submit { discoveryLoop() }
    }

    private fun workerLoop() {
        var browser: BrowserSession? = null
        var browserHeadless: Boolean? = null
        var offset = 0
        try {
            while (running && !Thread.currentThread().isInterrupted) {
                var task: CrawlTask? = null
                var permit: AutoCloseable? = null
                try {
                    val hosts = tasks.pendingSites()
                    for (index in hosts.indices) {
                        val host = hosts[(index + offset) % hosts.size]
                        val slot = limiter.tryAcquire(host) ?: continue
                        val candidate = try { tasks.claim(host, properties.leaseDuration) } catch (e: Exception) { slot.close(); throw e }
                        if (candidate == null) { slot.close(); continue }
                        task = candidate; permit = slot; offset++; break
                    }
                    if (task == null) {
                        Thread.sleep(400)
                        continue
                    }
                    val token = task.leaseToken!!
                    owned[task.id] = token
                    if (task.stage != CrawlStage.SAVE) {
                        val adapter = sources.adapter(task.sourceId)
                        val headless = properties.headless && !adapter.requiresWindowedBrowser
                        // 사이트에 필요한 모드가 바뀌면 이전 브라우저를 정리한다. 작업자당 하나만 유지한다.
                        if (browser != null && browserHeadless != headless) {
                            browser.close()
                            browser = null
                        }
                        val session = browser ?: BrowserSession(properties.copy(headless = headless), limiter).also {
                            browser = it
                            browserHeadless = headless
                        }
                        session.withPage { page ->
                            var posting = DiscoveredPosting(task.url, task.company, task.title, task.location)
                            var pageNavigated = false
                            fun navigateAndCheck() {
                                page.navigatePosting(posting.url)
                                pageNavigated = true
                                adapter.closureReason(page, posting)?.let { throw PostingClosed(it) }
                                check(page.responseStatus()?.let { it in 200..399 } != false) {
                                    "공고의 마감을 확인할 수 없는 HTTP ${page.responseStatus()} 응답: ${posting.url}"
                                }
                            }
                            // 기본 사이트는 기존 순서를 유지한다. API 우선 사이트는 상세 본문을 확인한 뒤
                            // 실제 새 캡처가 필요한 경우에만 원문 페이지를 연다.
                            if (task.stage == CrawlStage.BODY && !adapter.extractBeforeDetailNavigation) navigateAndCheck()
                            var checkpoint: CrawlTask = task
                            if (task.stage == CrawlStage.BODY) {
                                val extracted = try { adapter.extract(page, posting) } catch (error: Exception) {
                                    // 클라이언트 리다이렉트/네이티브 알림이 늦게 나타나는 사이트도 재확인한다.
                                    if (error is PostingClosed || error is PostingExcluded || error is BrowserHttpFailure || !pageNavigated) throw error
                                    adapter.closureReason(page, posting)?.let { throw PostingClosed(it) }
                                    throw error
                                }
                                val scope = runs.findRun(task.runId)?.categoryIds.orEmpty().ifEmpty { setOf(task.categoryId) }
                                if (!scope.any { classifier.accepts(it, task.companyId, extracted) }) {
                                    throw PostingExcluded("선택한 분야의 회사별 판별 기준에서 제외: ${task.categoryId} / ${extracted.title}")
                                }
                                checkpoint = taskService.body(task.id, token, extracted)
                                posting = DiscoveredPosting(checkpoint.url, checkpoint.company, checkpoint.title, checkpoint.location)
                            }
                            // 동일 본문은 SAVE로 이동하여 캡처·버전 저장을 모두 생략한다.
                            // CAPTURE 재시도는 저장된 본문을 유지하고 렌더링·캡처만 수행한다.
                            if (checkpoint.stage == CrawlStage.CAPTURE) {
                                if (!pageNavigated) navigateAndCheck()
                                adapter.prepareCapture(page, posting, checkpoint.bodyText)
                                val path = captures.save(page.capture())
                                taskService.captured(task.id, token, path)
                            }
                        }
                    }
                    taskService.finish(task.id, token)
                } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
                catch (e: PostingClosed) { task?.let { current ->
                    try { taskService.closeExisting(current.id, current.leaseToken!!, e.reason) }
                    catch (failure: Exception) {
                        log.warn("마감 상태 저장 실패: {}", current.id, failure)
                        try { taskService.fail(current.id, current.leaseToken!!, failure) } catch (_: LeaseLost) { }
                        catch (saveFailure: Exception) { log.warn("마감 실패 기록 저장 실패; 임대 만료 후 복구합니다: {}", current.id, saveFailure) }
                    }
                } }
                catch (e: PostingExcluded) { task?.let { current ->
                    try { taskService.exclude(current.id, current.leaseToken!!, e.message ?: "사이트 규칙에 따라 제외") }
                    catch (lost: Exception) { log.warn("제외 상태 저장 실패; 임대 만료 후 복구합니다: {}", current.id, lost) }
                } }
                catch (e: Exception) {
                    if (task != null) {
                        try { taskService.fail(task.id, task.leaseToken!!, e) } catch (_: LeaseLost) { log.info("작업 임대 만료: {}", task.id) }
                        catch (failure: Exception) { log.warn("실패 상태 저장 실패; 임대 만료 후 복구합니다: {}", task.id, failure) }
                    } else { log.warn("큐 조회 실패", e); Thread.sleep(1000) }
                    // 브라우저 장애는 다음 작업에서 새 세션으로 복구한다. 정리도 동일 스레드에서 수행한다.
                    try { browser?.close() } catch (_: Exception) { }
                    browser = null
                } finally { task?.let { owned.remove(it.id) }; permit?.close() }
            }
        } finally { try { browser?.close() } catch (_: Exception) { } }
    }

    private fun applicationPageWorkerLoop() {
        var browser: BrowserSession? = null
        fun process(publicFetch: PublicPageFetchLease) {
            ownedPublicFetches[publicFetch.id] = publicFetch.leaseToken
            try {
                val session = browser ?: BrowserSession(properties.copy(headless = true), limiter, publicOnly = true).also { browser = it }
                fetchPublicApplicationPage(publicFetch, session)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Exception) {
                try { applicationRequirements.failPublicPage(publicFetch.id, publicFetch.leaseToken, error.message ?: "공개 지원 페이지를 읽지 못했습니다.") }
                catch (_: LeaseLost) { log.info("공개 페이지 작업 임대 만료: {}", publicFetch.id) }
                catch (failure: Exception) { log.warn("공개 페이지 실패 상태 저장 실패: {}", publicFetch.id, failure) }
                log.info("공개 지원 페이지 확인 실패: {} · {}", publicFetch.id, error.message)
                try { browser?.close() } catch (_: Exception) { }; browser = null
            } finally { ownedPublicFetches.remove(publicFetch.id) }
        }
        try {
            while (running && !Thread.currentThread().isInterrupted) {
                try {
                    val publicFetch = applicationRequirements.claimPublicPage()
                    if (publicFetch == null) Thread.sleep(400) else process(publicFetch)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (error: Exception) {
                    log.warn("지원 페이지 작업 큐 조회 실패", error)
                    Thread.sleep(1000)
                }
            }
        } finally { try { browser?.close() } catch (_: Exception) { } }
    }

    private fun discoveryLoop() {
        var browser: BrowserSession? = null
        try {
            while (running && !Thread.currentThread().isInterrupted) {
                var source: RunSource? = null
                try {
                    source = runs.nextDiscovery()
                    if (source == null) { Thread.sleep(700); continue }
                    discovering = source.id
                    val adapter = sources.adapter(source.sourceId)
                    val session = browser ?: BrowserSession(properties, limiter, manageSitePermit = true).also { browser = it }
                    session.withPage { page -> adapter.discoverAt(page, source.listingUrl.ifBlank { adapter.definition.listingUrl }) { collections.enqueue(source.runId, source.sourceId, it) } }
                    collections.discovered(source)
                } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
                catch (e: Exception) {
                    log.warn("목록 수집 실패: {}", source?.sourceId, e)
                    source?.let { current ->
                        try { runs.saveSource(current.copy(status = DiscoveryStatus.FAILED, error = e.stackTraceToString().take(24_000), failedAt = Instant.now())) }
                        catch (failure: Exception) { log.warn("목록 실패 상태 저장 실패; 다음 복구 주기에 재개합니다.", failure) }
                    }
                    try { browser?.close() } catch (_: Exception) { }; browser = null
                    if (source == null) Thread.sleep(1000)
                } finally { discovering = null }
            }
        } finally { try { browser?.close() } catch (_: Exception) { } }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    fun maintain() {
        if (!running) return
        if (discovering == null) runs.recoverDiscovery()
        owned.forEach { (id, token) -> tasks.renew(id, token, properties.leaseDuration) }
        ownedPublicFetches.forEach { (id, token) -> applicationRequirements.renewPublicPage(id, token) }
        tasks.recoverExpired(Instant.now())
        runs.recentRuns().filter { it.active }.forEach { runs.finishIfReady(it.id, Instant.now()) }
    }

    private fun fetchPublicApplicationPage(fetch: PublicPageFetchLease, session: BrowserSession) {
        session.withPage { page ->
            fun navigatePublic(url: String) {
                PublicUrlGuard.validate(url)
                val host = PostingUrl.siteKey(url)
                var slot: AutoCloseable? = null
                while (slot == null) { slot = limiter.tryAcquire(host); if (slot == null) Thread.sleep(200) }
                try { limiter.beforeNavigation(host); page.navigate(url) } finally { slot.close() }
                page.evaluate("() => new Promise(resolve => setTimeout(resolve, 700))")
                val resolved = page.currentUrl().orEmpty()
                PublicUrlGuard.validate(resolved)
                check(page.responseStatus()?.let { it in 200..399 } != false) { "공개 페이지가 HTTP ${page.responseStatus()} 응답을 반환했습니다." }
                check(!resolved.contains(Regex("(?i)/(login|signin|sign-in|auth)(/|$|\\?)"))) { "지원 페이지에서 로그인을 요구합니다. 로그인하지 않고 직접 복사해 주세요." }
            }

            navigatePublic(fetch.postingUrl)
            val postingUrl = page.currentUrl().orEmpty()
            val links = page.evaluate("""() => Array.from(document.querySelectorAll('a[href]')).map(a => ({href:a.href, text:(a.innerText || a.getAttribute('aria-label') || '').trim()})).filter(x => /지원|접수|apply|application|submit/i.test(x.text + ' ' + x.href)).filter(x => x.href && !/login|signin|sign-in|logout|terms|privacy/i.test(x.href + ' ' + x.text)).slice(0, 24)""") as? List<*> ?: emptyList<Any>()
            val candidates = links.mapNotNull { raw ->
                val row = raw as? Map<*, *> ?: return@mapNotNull null
                val url = row["href"]?.toString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                url
            }.distinct().filterNot { samePublicUrl(it, postingUrl) }.take(12)

            var selectedText: String? = null
            var selectedUrl = postingUrl
            var loginRequired = false
            // 원래 공고 본문은 폼처럼 보이는 요소가 있더라도 지원 페이지 원문으로 사용하지 않는다.
            for (url in candidates) {
                if (selectedText != null) break
                try {
                    navigatePublic(url)
                    val text = visibleText(page)
                    val resolvedUrl = page.currentUrl().orEmpty()
                    if (looksLikeLoginPage(page, text)) {
                        loginRequired = true
                        continue
                    }
                    if (!samePublicUrl(resolvedUrl, postingUrl) && text.length >= ApplicationRequirementService.MIN_PUBLIC_TEXT_CHARS) {
                        selectedText = text
                        selectedUrl = resolvedUrl
                    }
                } catch (error: Exception) {
                    if (error.message.orEmpty().contains("로그인")) loginRequired = true
                    // 한 링크가 비공개이거나 로그인으로 이어져도 다른 공개 지원 경로를 확인한다.
                    log.debug("공개 지원 링크 확인 생략: {} · {}", url, error.message)
                }
            }
            if (selectedText == null) {
                navigatePublic(fetch.postingUrl)
                val host = PostingUrl.siteKey(postingUrl)
                val textBeforeClick = visibleText(page)
                var clickPermit: AutoCloseable? = null
                while (clickPermit == null) { clickPermit = limiter.tryAcquire(host); if (clickPermit == null) Thread.sleep(200) }
                try {
                    limiter.beforeNavigation(host)
                    val clicked = page.evaluate("""() => {
                        const label = /지원하기|입사지원|지원서 작성|apply(?: now)?|submit application/i;
                        const button = [...document.querySelectorAll('button,[role="button"]')].find(el => {
                            const text = (el.innerText || el.getAttribute('aria-label') || '').trim();
                            const type = (el.getAttribute('type') || '').toLowerCase();
                            return label.test(text) && type !== 'submit' && !el.closest('form') && !el.matches(':disabled,[aria-disabled="true"]');
                        });
                        if (!button) return false;
                        button.click();
                        return true;
                    }""") as? Boolean ?: false
                    if (clicked) {
                        page.evaluate("() => new Promise(resolve => setTimeout(resolve, 1200))")
                        selectedUrl = page.currentUrl().orEmpty()
                        PublicUrlGuard.validate(selectedUrl)
                        val text = visibleText(page)
                        if (looksLikeLoginPage(page, text)) loginRequired = true
                        else {
                            val dialogText = page.evaluate("""() => (document.querySelector('[role="dialog"],[aria-modal="true"]')?.innerText || '').trim()""") as? String
                            val capturedText = dialogText?.takeIf { it.length >= ApplicationRequirementService.MIN_PUBLIC_TEXT_CHARS }
                                ?: text.takeIf { !samePublicUrl(selectedUrl, postingUrl) && text != textBeforeClick }
                            if (capturedText != null && capturedText.length >= ApplicationRequirementService.MIN_PUBLIC_TEXT_CHARS) selectedText = capturedText
                        }
                    }
                } finally { clickPermit.close() }
            }
            val content = selectedText ?: throw IllegalStateException(
                when {
                    loginRequired -> "지원 페이지에서 로그인이 필요합니다. 로그인하지 않고 직접 복사해 주세요."
                    candidates.isEmpty() -> "공개 지원 페이지 경로를 찾지 못했습니다. 공고 본문은 지원서 항목으로 사용하지 않았습니다."
                    else -> "지원 경로는 찾았지만 실제 지원 페이지 원문을 열지 못했습니다. 공고 본문은 지원서 항목으로 사용하지 않았습니다."
                },
            )
            applicationRequirements.completePublicPage(fetch.id, fetch.leaseToken, selectedUrl, content)
        }
    }

    private fun visibleText(page: BrowserPage): String = (page.evaluate("""() => document.body?.innerText || ''""") as? String)
        ?.replace(Regex("\\n{3,}"), "\n\n")?.trim()?.take(ApplicationRequirementService.MAX_TEXT_CHARS).orEmpty()

    private fun samePublicUrl(left: String, right: String): Boolean = runCatching {
        PostingUrl.normalize(left) == PostingUrl.normalize(right)
    }.getOrDefault(left.trimEnd('/') == right.trimEnd('/'))

    private fun looksLikeLoginPage(page: BrowserPage, text: String): Boolean {
        val url = page.currentUrl().orEmpty()
        return Regex("(?i)/(login|signin|sign-in|auth)(/|$|\\?)").containsMatchIn(url) ||
            Regex("로그인 후 이용|로그인하고 지원|sign in to apply|log in to continue", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    @PreDestroy
    fun stop() {
        running = false
        discovery.shutdownNow(); workers.shutdownNow(); applicationPageWorkers.shutdownNow()
        discovery.awaitTermination(3, TimeUnit.SECONDS); workers.awaitTermination(3, TimeUnit.SECONDS); applicationPageWorkers.awaitTermination(3, TimeUnit.SECONDS)
    }
}
