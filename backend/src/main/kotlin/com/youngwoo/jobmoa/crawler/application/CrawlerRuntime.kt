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
        discovery.submit { discoveryLoop() }
    }

    private fun workerLoop() {
        var browser: BrowserSession? = null
        var browserHeadless: Boolean? = null
        var browserPublicOnly = false
        var offset = 0
        var preferPublicFetch = true
        fun processPublicFetch(publicFetch: PublicPageFetchLease) {
            ownedPublicFetches[publicFetch.id] = publicFetch.leaseToken
            try {
                if (browser != null && (browserHeadless != true || !browserPublicOnly)) {
                    browser?.close(); browser = null
                }
                val session = browser ?: BrowserSession(properties.copy(headless = true), limiter, publicOnly = true).also {
                    browser = it; browserHeadless = true; browserPublicOnly = true
                }
                fetchPublicApplicationPage(publicFetch, session)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Exception) {
                try { applicationRequirements.failPublicPage(publicFetch.id, publicFetch.leaseToken, error.message ?: "공개 지원 페이지를 읽지 못했습니다.") }
                catch (_: LeaseLost) { log.info("공개 페이지 작업 임대 만료: {}", publicFetch.id) }
                catch (failure: Exception) { log.warn("공개 페이지 실패 상태 저장 실패: {}", publicFetch.id, failure) }
                log.info("공개 지원 페이지 확인 실패: {} · {}", publicFetch.id, error.message)
                try { browser?.close() } catch (_: Exception) { }; browser = null; browserPublicOnly = false; browserHeadless = null
            } finally { ownedPublicFetches.remove(publicFetch.id) }
        }
        try {
            while (running && !Thread.currentThread().isInterrupted) {
                var task: CrawlTask? = null
                var permit: AutoCloseable? = null
                try {
                    if (preferPublicFetch) {
                        val publicFetch = applicationRequirements.claimPublicPage()
                        if (publicFetch != null) {
                            processPublicFetch(publicFetch)
                            preferPublicFetch = false
                            continue
                        }
                    }
                    val hosts = tasks.pendingSites()
                    for (index in hosts.indices) {
                        val host = hosts[(index + offset) % hosts.size]
                        val slot = limiter.tryAcquire(host) ?: continue
                        val candidate = try { tasks.claim(host, properties.leaseDuration) } catch (e: Exception) { slot.close(); throw e }
                        if (candidate == null) { slot.close(); continue }
                        task = candidate; permit = slot; offset++; break
                    }
                    if (task == null) {
                        val publicFetch = applicationRequirements.claimPublicPage()
                        if (publicFetch != null) {
                            processPublicFetch(publicFetch)
                            preferPublicFetch = false
                        } else Thread.sleep(400)
                        continue
                    }
                    val token = task.leaseToken!!
                    owned[task.id] = token
                    if (task.stage != CrawlStage.SAVE) {
                        val adapter = sources.adapter(task.sourceId)
                        val headless = properties.headless && !adapter.requiresWindowedBrowser
                        // 사이트에 필요한 모드가 바뀌면 이전 브라우저를 정리한다. 작업자당 하나만 유지한다.
                        if (browser != null && browserHeadless != headless) {
                            browser?.close()
                            browser = null
                            browserPublicOnly = false
                        }
                        if (browserPublicOnly && browser != null) { browser?.close(); browser = null; browserHeadless = null; browserPublicOnly = false }
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
                    browserPublicOnly = false
                } finally { task?.let { owned.remove(it.id) }; permit?.close(); if (task != null) preferPublicFetch = true }
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
            val links = page.evaluate("""() => Array.from(document.querySelectorAll('a[href]')).map(a => ({href:a.href, text:(a.innerText || a.getAttribute('aria-label') || '').trim()})).filter(x => /지원하기|입사지원|지원서 작성|지원서|^지원$|apply(?: now)?|submit application/i.test(x.text)).filter(x => x.href && !/login|signin|sign-in|logout|terms|privacy/i.test(x.href + ' ' + x.text)).slice(0, 12)""") as? List<*> ?: emptyList<Any>()
            val candidates = links.mapNotNull { raw ->
                val row = raw as? Map<*, *> ?: return@mapNotNull null
                val url = row["href"]?.toString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val label = row["text"]?.toString().orEmpty()
                val score = (if (Regex("지원하기|입사지원|지원서 작성|apply(?: now)?|submit application", RegexOption.IGNORE_CASE).containsMatchIn(label)) 2 else 0) +
                    (if (Regex("/apply|/application|/job-application", RegexOption.IGNORE_CASE).containsMatchIn(url)) 1 else 0)
                url to score
            }.sortedByDescending { it.second }.take(5)

            var selectedText: String? = null
            var selectedUrl = page.currentUrl().orEmpty()
            val directText = visibleText(page)
            if (directText.isNotBlank() && isApplicationPage(page, directText)) selectedText = directText
            for ((url, _) in candidates) {
                if (selectedText != null) break
                try {
                    navigatePublic(url)
                    val text = visibleText(page)
                    if (text.isNotBlank() && isApplicationPage(page, text) && !looksLikeLoginPage(page, text)) {
                        selectedText = text
                        selectedUrl = page.currentUrl().orEmpty()
                    }
                } catch (error: Exception) {
                    // 한 링크가 비공개이거나 로그인으로 이어져도 다른 공개 지원 링크는 확인한다.
                    log.debug("공개 지원 링크 확인 생략: {} · {}", url, error.message)
                }
            }
            val content = selectedText ?: throw IllegalStateException(
                if (candidates.isEmpty()) "공개 지원 링크를 찾지 못했습니다. 빈 문항을 직접 복사해 주세요."
                else "공개 문항을 확인하지 못했습니다. 로그인 없이 볼 수 있는 문항을 직접 복사해 주세요.",
            )
            applicationRequirements.completePublicPage(fetch.id, fetch.leaseToken, selectedUrl, content)
        }
    }

    private fun visibleText(page: BrowserPage): String = (page.evaluate("""() => document.body?.innerText || ''""") as? String)
        ?.replace(Regex("\\n{3,}"), "\n\n")?.trim()?.take(ApplicationRequirementService.MAX_TEXT_CHARS).orEmpty()

    private fun isApplicationPage(page: BrowserPage, text: String): Boolean {
        val hasForm = page.evaluate("""() => !!document.querySelector('textarea, input[type="file"], input[type="radio"], input[type="checkbox"]') || document.querySelectorAll('form input:not([type="hidden"]):not([type="search"])').length >= 3""") as? Boolean ?: false
        return hasForm || Regex("지원서|자기소개서|제출 서류|resume|cover letter|application question", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    private fun looksLikeLoginPage(page: BrowserPage, text: String): Boolean {
        val url = page.currentUrl().orEmpty()
        return Regex("(?i)/(login|signin|sign-in|auth)(/|$|\\?)").containsMatchIn(url) ||
            Regex("로그인 후 이용|로그인하고 지원|sign in to apply|log in to continue", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    @PreDestroy
    fun stop() {
        running = false
        discovery.shutdownNow(); workers.shutdownNow()
        discovery.awaitTermination(3, TimeUnit.SECONDS); workers.awaitTermination(3, TimeUnit.SECONDS)
    }
}
