package com.youngwoo.jobmoa.collection.application

import com.youngwoo.jobmoa.collection.domain.model.*
import com.youngwoo.jobmoa.collection.domain.repository.*
import com.youngwoo.jobmoa.crawler.application.SourceRegistry
import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.shared.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class CollectionService(
    private val runs: CollectionRepository,
    private val tasks: CrawlTaskRepository,
    private val sites: SourceRegistry,
) {
    @Transactional
    fun start(sourceIds: List<String>, targets: List<DirectTarget> = emptyList()): String {
        val sources = sourceIds.distinct()
        require(sources.isNotEmpty() && sources.size <= sites.definitions.size) { "수집할 기업군을 선택하세요." }
        sources.forEach { sites.adapter(it) }
        require(targets.all { it.sourceId in sources }) { "직접 지정한 공고의 기업군이 수집 대상에 포함되어야 합니다." }
        val run = CollectionRun(UUID.randomUUID().toString(), Instant.now())
        runs.activate(run, sources.map { source -> RunSource("${run.id}:$source", run.id, source,
            if (targets.isEmpty()) DiscoveryStatus.PENDING else DiscoveryStatus.COMPLETED) })
        targets.forEach { enqueue(run.id, it.sourceId, DiscoveredPosting(it.url, it.company.ifBlank { sites.definition(it.sourceId).name }, it.title, it.location)) }
        return run.id
    }

    @Transactional
    fun enqueue(runId: String, sourceId: String, posting: DiscoveredPosting): Boolean {
        val run = runs.findRun(runId) ?: throw ResourceNotFound("수집 실행을 찾을 수 없습니다.")
        require(run.active) { "종료된 실행에는 공고를 추가할 수 없습니다." }
        require(runs.sources(runId).any { it.sourceId == sourceId })
        val adapter = sites.adapter(sourceId)
        val url = PostingUrl.normalize(posting.url)
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
        runs.activate(CollectionRun(id, Instant.now(), retryOf = runId), sourceIds.map { source ->
            RunSource("$id:$source", id, source,
                if (sourceFailures.any { it.sourceId == source }) DiscoveryStatus.PENDING else DiscoveryStatus.COMPLETED,
                retryCount = (sourceFailures.find { it.sourceId == source }?.retryCount ?: 0) + 1,
                retryOfSourceId = sourceFailures.find { it.sourceId == source }?.id)
        })
        failures.forEach { tasks.enqueue(it.retry(id, UUID.randomUUID().toString())) }
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
