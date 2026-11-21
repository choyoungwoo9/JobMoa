package com.youngwoo.jobmoa.crawler.application

import com.youngwoo.jobmoa.collection.application.*
import com.youngwoo.jobmoa.collection.domain.model.*
import com.youngwoo.jobmoa.collection.domain.repository.*
import com.youngwoo.jobmoa.crawler.domain.model.*
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
    private val limiter: SiteLimiter, private val captures: CaptureStorage, private val classifier: CategoryClassifier) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sequence = AtomicInteger()
    private val workers = Executors.newFixedThreadPool(properties.workers) { r -> Thread(r, "crawl-worker-${sequence.incrementAndGet()}") }
    private val discovery = Executors.newSingleThreadExecutor { r -> Thread(r, "crawl-discovery") }
    private val owned = ConcurrentHashMap<String, String>()
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
                    if (task == null) { Thread.sleep(400); continue }
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
        tasks.recoverExpired(Instant.now())
        runs.recentRuns().filter { it.active }.forEach { runs.finishIfReady(it.id, Instant.now()) }
    }

    @PreDestroy
    fun stop() {
        running = false
        discovery.shutdownNow(); workers.shutdownNow()
        discovery.awaitTermination(3, TimeUnit.SECONDS); workers.awaitTermination(3, TimeUnit.SECONDS)
    }
}
