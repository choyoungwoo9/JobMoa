package com.youngwoo.jobmoa.collection.application

import com.youngwoo.jobmoa.collection.domain.model.*
import com.youngwoo.jobmoa.collection.domain.repository.*
import com.youngwoo.jobmoa.crawler.application.SourceRegistry
import com.youngwoo.jobmoa.crawler.application.CompanyRegistry
import com.youngwoo.jobmoa.posting.domain.model.Posting
import com.youngwoo.jobmoa.posting.domain.repository.PostingRepository
import com.youngwoo.jobmoa.shared.ResourceNotFound
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class WorkspaceQuery(private val changes: CollectionChangeRepository, private val maintenance: com.youngwoo.jobmoa.workspace.application.WorkspaceRevision, private val runs: CollectionRepository, private val tasks: CrawlTaskRepository,
    private val postings: PostingRepository, private val sites: SourceRegistry, private val companies: CompanyRegistry, private val interests: com.youngwoo.jobmoa.interest.domain.repository.PostingInterestRepository) {
    fun sources() = sites.definitions.map { definition -> mapOf(
        "id" to definition.id, "name" to definition.name, "url" to definition.listingUrl,
        "available" to sites.supported(definition.id),
        "portals" to if (sites.supported(definition.id)) sites.adapter(definition.id).discoveryTargets else emptyList<Any>(),
    ) }
    fun state() = mapOf("version" to 1, "workspaceRevision" to maintenance.current(), "jobs" to jobs(), "runs" to collections(), "sources" to sources(), "categories" to companies.categories, "companies" to companies.list())
    fun jobs(): List<Map<String, Any?>> {
        val all = postings.list()
        val management = interests.findAll(all.map { it.id })
        val versions = postings.versionSummaries(all.map { it.id }).groupBy { it.postingId }
        val latest = runs.recentRuns(1).firstOrNull()?.let { tasks.summaries(it.id).associateBy { t -> t.urlHash } }.orEmpty()
        return all.map { posting -> metadata(posting, latest[posting.urlHash]?.outcome?.name?.lowercase()) + ("interest" to (management[posting.id] ?: com.youngwoo.jobmoa.interest.domain.model.PostingInterest(posting.id))) +
            ("versions" to versions[posting.id].orEmpty().map { mapOf("id" to it.id.toString(), "savedAt" to it.savedAt,
                "body" to "", "bodyLoaded" to false, "captureAvailable" to true, "captureUrl" to "/api/captures/${it.capturePath}") }) }
    }
    fun job(id: Long): Map<String, Any?> {
        val posting = postings.find(id) ?: throw ResourceNotFound("공고를 찾을 수 없습니다.")
        return metadata(posting, posting.lastChange) + ("interest" to (interests.find(id) ?: com.youngwoo.jobmoa.interest.domain.model.PostingInterest(id))) + ("versions" to postings.versions(id).map {
            mapOf("id" to it.id.toString(), "savedAt" to it.savedAt, "body" to it.bodyText, "bodyLoaded" to true,
                "captureAvailable" to true, "captureUrl" to "/api/captures/${it.capturePath}")
        })
    }
    private fun metadata(p: Posting, change: String?) = mapOf(
        "id" to p.id.toString(), "sourceId" to p.sourceId, "companyId" to companies.resolve(p.sourceId, p.company, p.url).firstOrNull()?.id, "company" to p.company, "title" to p.title,
        "location" to p.location, "url" to p.url, "firstSeen" to p.firstSeen, "read" to p.read, "closed" to p.closed,
        "categories" to p.categories.sorted(), "classification" to p.classification,
        "latestChange" to change?.takeIf { it == "new" || it == "changed" },
    )
    fun collections() = runs.recentRuns().map(::collection)
    fun collection(id: String) = collection(runs.findRun(id) ?: throw ResourceNotFound("수집 실행을 찾을 수 없습니다."))
    fun changes(id: String): Map<String, Any?> {
        val run = runs.findRun(id) ?: throw ResourceNotFound("수집 실행을 찾을 수 없습니다.")
        val events = changes.list(id)
        fun ids(predicate: (CollectionChange) -> Boolean) = events.filter(predicate).map { it.postingId.toString() }.distinct()
        val legacy = events.any { it.wasStarred == null || it.becameClosed == null }
        return mapOf("runId" to id, "finished" to !run.active, "legacy" to legacy,
            "new" to ids { it.outcome == "NEW" }, "changed" to ids { it.outcome == "CHANGED" },
            "closed" to ids { it.becameClosed == true },
            "interestChanged" to ids { it.wasStarred == true && it.outcome == "CHANGED" },
            "interestClosed" to ids { it.wasStarred == true && it.becameClosed == true })
    }
    fun taskList(id: String): List<TaskSummary> {
        if (runs.findRun(id) == null) throw ResourceNotFound("수집 실행을 찾을 수 없습니다.")
        return tasks.summaries(id)
    }
    private fun collection(run: CollectionRun): Map<String, Any?> {
        val all = tasks.summaries(run.id)
        val portals = runs.sources(run.id)
        val discoveryFinished = portals.all { it.status == DiscoveryStatus.COMPLETED || it.status == DiscoveryStatus.FAILED }
        val terminal = all.count { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED }
        return mapOf(
            "id" to run.id, "startedAt" to run.startedAt, "finishedAt" to run.finishedAt, "retryOf" to run.retryOf,
            "categoryId" to run.categoryId, "categoryIds" to run.categoryIds.ifEmpty { setOf(run.categoryId) }.sorted(), "companyIds" to run.companyIds.sorted(),
            "status" to run.status, "progress" to mapOf("discoveryFinished" to discoveryFinished, "total" to all.size,
                "pending" to all.count { it.status == TaskStatus.PENDING }, "processing" to all.count { it.status == TaskStatus.PROCESSING },
                "completed" to all.count { it.status == TaskStatus.COMPLETED }, "failed" to all.count { it.status == TaskStatus.FAILED },
                "percent" to if (!discoveryFinished) null else if (all.isEmpty()) 100 else terminal * 100 / all.size),
            "results" to portals.groupBy { it.sourceId }.map { (sourceId, sourcePortals) ->
                val group = all.filter { it.sourceId == sourceId }
                val failedPortals = sourcePortals.filter { it.status == DiscoveryStatus.FAILED }
                val unknown = failedPortals.size == sourcePortals.size && group.isEmpty()
                val waiting = sourcePortals.all { it.status == DiscoveryStatus.PENDING }
                val pending = sourcePortals.any { it.status == DiscoveryStatus.PENDING || it.status == DiscoveryStatus.RUNNING } ||
                    group.any { it.status == TaskStatus.PENDING || it.status == TaskStatus.PROCESSING }
                val failures = group.filter { it.status == TaskStatus.FAILED }.map { t -> mapOf(
                    "id" to t.id, "sourceId" to t.sourceId, "company" to t.company, "title" to t.title, "url" to t.url,
                    "stage" to t.stage.name.lowercase(), "at" to t.failedAt, "attempts" to t.attempts,
                    "error" to t.error, "resolved" to (t.resolvedAt != null),
                ) } + failedPortals.map { p -> mapOf(
                    "id" to "list:${p.id}", "sourceId" to sourceId,
                    "company" to p.label.ifBlank { sites.definition(sourceId).name },
                    "url" to p.listingUrl.ifBlank { sites.definition(sourceId).listingUrl }, "stage" to "list",
                    "at" to p.failedAt, "attempts" to p.retryCount + 1, "error" to p.error, "resolved" to (p.resolvedAt != null),
                ) }
                val succeeded = group.count { it.status == TaskStatus.COMPLETED && it.outcome != TaskOutcome.EXCLUDED }
                mapOf("sourceId" to sourceId, "status" to when {
                    pending -> if (waiting && group.isEmpty()) "waiting" else "running"
                    unknown || failures.isNotEmpty() && succeeded == 0 && group.isNotEmpty() -> "failed"
                    failures.isNotEmpty() -> "partial"
                    else -> "success"
                }, "added" to if (unknown) null else group.count { it.outcome == TaskOutcome.NEW },
                    "changed" to if (unknown) null else group.count { it.outcome == TaskOutcome.CHANGED },
                    "closed" to if (unknown) null else group.count { it.status == TaskStatus.COMPLETED && it.outcome != TaskOutcome.EXCLUDED && it.closed },
                    "identical" to if (unknown) null else group.count { it.outcome == TaskOutcome.IDENTICAL },
                    "failed" to if (unknown) null else failures.size,
                    "succeeded" to succeeded, "excluded" to group.count { it.outcome == TaskOutcome.EXCLUDED }, "total" to group.size,
                    "recovered" to group.count { it.status == TaskStatus.COMPLETED && it.retryOfTaskId != null }, "failures" to failures,
                    "portals" to sourcePortals.map { mapOf("name" to it.label.ifBlank { sites.definition(sourceId).name },
                        "url" to it.listingUrl.ifBlank { sites.definition(sourceId).listingUrl }, "status" to it.status) },
                )
            },
        )
    }
}
