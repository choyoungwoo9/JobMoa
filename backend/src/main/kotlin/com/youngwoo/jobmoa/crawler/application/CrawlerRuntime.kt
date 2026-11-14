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
class CrawlerRuntime(private val properties: CrawlerProperties, private val tasks: CrawlTaskRepository,
    private val runs: CollectionRepository, private val collections: CollectionService,
    private val taskService: TaskService, private val sources: SourceRegistry,
    private val limiter: SiteLimiter, private val captures: CaptureStorage) {
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
                        val session = browser ?: BrowserSession(properties, limiter).also { browser = it }
                        session.withPage { page ->
                            page.navigate(task.url)
                            if (task.stage == CrawlStage.BODY) {
                                val extracted = sources.adapter(task.sourceId).extract(page, DiscoveredPosting(task.url, task.company, task.title, task.location))
                                taskService.body(task.id, token, extracted)
                            }
                            // 캡처 재시도는 저장된 본문을 유지하고 렌더링·캡처만 수행한다.
                            sources.adapter(task.sourceId).prepareCapture(page, DiscoveredPosting(task.url, task.company, task.title, task.location))
                            val path = captures.save(page.capture())
                            taskService.captured(task.id, token, path)
                        }
                    }
                    taskService.finish(task.id, token)
                } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
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
