package com.youngwoo.jobmoa.collection.application

import com.youngwoo.jobmoa.collection.domain.model.*
import com.youngwoo.jobmoa.collection.domain.repository.*
import com.youngwoo.jobmoa.crawler.application.SourceRegistry
import com.youngwoo.jobmoa.posting.domain.model.Posting
import com.youngwoo.jobmoa.posting.domain.repository.PostingRepository
import com.youngwoo.jobmoa.shared.ResourceNotFound
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service @Transactional(readOnly = true)
class WorkspaceQuery(private val runs: CollectionRepository, private val tasks: CrawlTaskRepository,
    private val postings: PostingRepository, private val sites: SourceRegistry) {
    fun sources() = sites.definitions.map { mapOf("id" to it.id, "name" to it.name, "url" to it.listingUrl, "available" to sites.supported(it.id)) }
    fun state() = mapOf("version" to 1, "jobs" to jobs(), "runs" to collections(), "sources" to sources())
    fun jobs() = postings.list().map(::posting)
    fun job(id: Long) = posting(postings.find(id) ?: throw ResourceNotFound("공고를 찾을 수 없습니다."))
    private fun posting(p: Posting): Map<String, Any?> = mapOf(
        "id" to p.id.toString(), "sourceId" to p.sourceId, "company" to p.company, "title" to p.title,
        "location" to p.location, "url" to p.url, "firstSeen" to p.firstSeen, "read" to p.read, "closed" to p.closed,
        "latestChange" to p.lastChange?.takeIf { it == "new" || it == "changed" },
        "versions" to postings.versions(p.id).map { mapOf("id" to it.id.toString(), "savedAt" to it.savedAt,
            "body" to it.bodyText, "captureAvailable" to true, "captureUrl" to "/api/captures/${it.capturePath}") },
    )
    fun collections() = runs.recentRuns().map(::collection)
    fun collection(id: String) = collection(runs.findRun(id) ?: throw ResourceNotFound("수집 실행을 찾을 수 없습니다."))
    fun taskList(id: String): List<Map<String, Any?>> {
        if (runs.findRun(id) == null) throw ResourceNotFound("수집 실행을 찾을 수 없습니다.")
        return tasks.tasks(id).map { mapOf("id" to it.id, "sourceId" to it.sourceId,
            "company" to it.company, "title" to it.title, "url" to it.url,
            "status" to it.status, "stage" to it.stage, "attempts" to it.attempts,
            "outcome" to it.outcome, "failedAt" to it.failedAt, "error" to it.error) }
    }
    private fun collection(run: CollectionRun): Map<String, Any?> {
        val all = tasks.tasks(run.id)
        val sourceResults = runs.sources(run.id)
        val discoveryFinished = sourceResults.all { it.status == DiscoveryStatus.COMPLETED || it.status == DiscoveryStatus.FAILED }
        val terminal = all.count { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED }
        return mapOf("id" to run.id, "startedAt" to run.startedAt, "finishedAt" to run.finishedAt, "retryOf" to run.retryOf,
            "status" to run.status, "progress" to mapOf("discoveryFinished" to discoveryFinished, "total" to all.size,
                "pending" to all.count { it.status == TaskStatus.PENDING }, "processing" to all.count { it.status == TaskStatus.PROCESSING },
                "completed" to all.count { it.status == TaskStatus.COMPLETED }, "failed" to all.count { it.status == TaskStatus.FAILED },
                "percent" to if (!discoveryFinished) null else if (all.isEmpty()) 100 else terminal * 100 / all.size),
            "results" to sourceResults.map { source ->
                val group = all.filter { it.sourceId == source.sourceId }
                val failedList = source.status == DiscoveryStatus.FAILED
                val unknown = failedList && group.isEmpty()
                val pending = source.status in listOf(DiscoveryStatus.PENDING, DiscoveryStatus.RUNNING) || group.any { it.status in listOf(TaskStatus.PENDING, TaskStatus.PROCESSING) }
                val failed = group.filter { it.status == TaskStatus.FAILED }
                val failures = failed.map { t -> mapOf("id" to t.id, "sourceId" to t.sourceId, "company" to t.company,
                    "title" to t.title, "url" to t.url, "stage" to t.stage.name.lowercase(), "at" to t.failedAt,
                    "attempts" to t.attempts, "error" to t.error, "resolved" to (t.resolvedAt != null)) } +
                    if (failedList) listOf(mapOf("id" to "list:${source.id}", "sourceId" to source.sourceId,
                        "company" to sites.definition(source.sourceId).name, "url" to sites.definition(source.sourceId).listingUrl,
                        "stage" to "list", "at" to source.failedAt, "attempts" to source.retryCount + 1, "error" to source.error, "resolved" to (source.resolvedAt != null))) else emptyList()
                mapOf("sourceId" to source.sourceId, "status" to when {
                    pending -> if (source.status == DiscoveryStatus.PENDING) "waiting" else "running"
                    unknown -> "failed"
                    failures.isNotEmpty() -> "partial"
                    else -> "success"
                }, "added" to if (unknown) null else group.count { it.outcome == TaskOutcome.NEW },
                    "changed" to if (unknown) null else group.count { it.outcome == TaskOutcome.CHANGED },
                    "identical" to if (unknown) null else group.count { it.outcome == TaskOutcome.IDENTICAL },
                    "failed" to if (unknown) null else failures.size,
                    "succeeded" to group.count { it.status == TaskStatus.COMPLETED && it.outcome != TaskOutcome.EXCLUDED },
                    "excluded" to group.count { it.outcome == TaskOutcome.EXCLUDED }, "total" to group.size,
                    "recovered" to group.count { it.status == TaskStatus.COMPLETED && it.retryOfTaskId != null }, "failures" to failures)
            })
    }
}
