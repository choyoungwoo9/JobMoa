package com.youngwoo.jobmoa.collection.application

import com.youngwoo.jobmoa.collection.domain.model.*
import com.youngwoo.jobmoa.collection.domain.repository.*
import com.youngwoo.jobmoa.crawler.application.SourceRegistry
import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.shared.*
import com.youngwoo.jobmoa.posting.domain.repository.PostingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class CollectionService(
    private val runs: CollectionRepository,
    private val tasks: CrawlTaskRepository,
    private val sites: SourceRegistry,
    private val postings: PostingRepository,
) {
    @Transactional
    fun start(sourceIds: List<String>, targets: List<DirectTarget> = emptyList()): String {
        val sources = sourceIds.distinct()
        require(sources.isNotEmpty() && sources.size <= sites.definitions.size) { "수집할 기업군을 선택하세요." }
        sources.forEach { sites.adapter(it) }
        require(targets.all { it.sourceId in sources }) { "직접 지정한 공고의 기업군이 수집 대상에 포함되어야 합니다." }
        val run = CollectionRun(UUID.randomUUID().toString(), Instant.now())
        runs.activate(run, sources.flatMap { source ->
            val portals = if (targets.isEmpty()) sites.adapter(source).discoveryTargets else listOf(sites.definition(source))
            portals.map { portal -> RunSource("${run.id}:${portal.id}", run.id, source,
                if (targets.isEmpty()) DiscoveryStatus.PENDING else DiscoveryStatus.COMPLETED,
                listingUrl = portal.listingUrl, label = portal.name) }
        })
        targets.forEach { enqueue(run.id, it.sourceId, DiscoveredPosting(it.url, it.company.ifBlank { sites.definition(it.sourceId).name }, it.title, it.location)) }
        if (targets.isEmpty()) postings.list().filter { !it.closed && it.sourceId in sources }.forEach {
            enqueue(run.id, it.sourceId, DiscoveredPosting(it.url, it.company, it.title, it.location))
        }
        return run.id
    }

    @Transactional
    fun enqueue(runId: String, sourceId: String, posting: DiscoveredPosting): Boolean {
        val run = runs.findRun(runId) ?: throw ResourceNotFound("수집 실행을 찾을 수 없습니다.")
        require(run.active) { "종료된 실행에는 공고를 추가할 수 없습니다." }
        require(runs.sources(runId).any { it.sourceId == sourceId })
        val adapter = sites.adapter(sourceId)
        val url = PostingUrl.normalize(adapter.canonicalizeUrl(PostingUrl.normalize(posting.url)))
        require(adapter.acceptsUrl(url)) { "수집기가 지원하지 않는 공고 주소입니다." }
        return tasks.enqueue(CrawlTask(UUID.randomUUID().toString(), runId, sourceId,
            posting.company.take(255), url, PostingUrl.hash(url), PostingUrl.siteKey(url), Instant.now(),
            posting.title.take(1000), posting.location.take(255)))
    }

    @Transactional
    fun retry(runId: String, failureIds: List<String>?): String {
        val previous = runs.findRun(runId) ?: throw ResourceNotFound("수집 실행을 찾을 수 없습니다.")
        require(!previous.active) { "실행이 끝난 뒤 재시도할 수 있습니다." }
        val failures = tasks.tasks(runId).filter { it.status == TaskStatus.FAILED && it.resolvedAt == null && (failureIds == null || it.id in failureIds) }
        val sourceFailures = runs.sources(runId).filter { it.status == DiscoveryStatus.FAILED && it.resolvedAt == null && (failureIds == null || "list:${it.id}" in failureIds) }
        require(failures.isNotEmpty() || sourceFailures.isNotEmpty()) { "재시도할 실패 항목이 없습니다." }
        val id = UUID.randomUUID().toString()
        val sourceIds = (failures.map { it.sourceId } + sourceFailures.map { it.sourceId }).distinct()
        val retrySources = sourceFailures.mapIndexed { index, previousSource ->
            previousSource.copy(id = "$id:retry-$index", runId = id, status = DiscoveryStatus.PENDING,
                error = null, failedAt = null, retryCount = previousSource.retryCount + 1,
                retryOfSourceId = previousSource.id, resolvedAt = null)
        } + sourceIds.filter { source -> sourceFailures.none { it.sourceId == source } }.map { source ->
            RunSource("$id:$source", id, source, DiscoveryStatus.COMPLETED)
        }
        runs.activate(CollectionRun(id, Instant.now(), retryOf = runId), retrySources)
        failures.forEach { failed ->
            val adapter = sites.adapter(failed.sourceId)
            val retried = failed.retry(id, UUID.randomUUID().toString())
            // 본문 단계에서 잘못 생성된 과거 주소도 수정된 사이트 규칙으로 재시도한다.
            // 캡처/저장 체크포인트는 이미 추출한 본문과 주소를 그대로 유지한다.
            val url = if (failed.stage == CrawlStage.BODY) PostingUrl.normalize(adapter.canonicalizeUrl(failed.url)) else failed.url
            require(adapter.acceptsUrl(url)) { "수집기가 지원하지 않는 재시도 주소입니다: $url" }
            tasks.enqueue(retried.copy(url = url, urlHash = PostingUrl.hash(url), siteKey = PostingUrl.siteKey(url)))
        }
        return id
    }

    @Transactional
    fun discovered(source: RunSource) {
        val now = Instant.now()
        runs.saveSource(source.copy(status = DiscoveryStatus.COMPLETED))
        var previousId = source.retryOfSourceId
        val visited = mutableSetOf<String>()
        while (previousId != null && visited.add(previousId)) {
            val previousRun = previousId.substringBefore(':')
            val previous = runs.sources(previousRun).find { it.id == previousId } ?: break
            runs.saveSource(previous.copy(resolvedAt = now))
            previousId = previous.retryOfSourceId
        }
    }
}

data class DirectTarget(val sourceId: String, val url: String, val company: String = "", val title: String = "", val location: String = "")
