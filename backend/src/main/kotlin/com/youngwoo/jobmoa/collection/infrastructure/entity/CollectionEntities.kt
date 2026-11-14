package com.youngwoo.jobmoa.collection.infrastructure.entity

import com.youngwoo.jobmoa.collection.domain.model.*
import jakarta.persistence.*
import java.time.Instant

@Entity @Table(name = "collection_lock")
open class CollectionLockEntity(
    @field:Id open var id: String = "collection",
    @field:Column(name = "active_run_id") open var activeRunId: String? = null,
)

@Entity @Table(name = "collection_run")
open class CollectionRunEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "started_at", nullable = false) open var startedAt: Instant = Instant.EPOCH,
    @field:Column(nullable = false) open var status: String = RunStatus.DISCOVERING.name,
    @field:Column(name = "finished_at") open var finishedAt: Instant? = null,
    @field:Column(name = "retry_of") open var retryOf: String? = null,
) {
    fun domain() = CollectionRun(id, startedAt, RunStatus.valueOf(status), finishedAt, retryOf)
}
fun CollectionRun.entity() = CollectionRunEntity(id, startedAt, status.name, finishedAt, retryOf)

@Entity @Table(name = "collection_run_source")
open class RunSourceEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "run_id", nullable = false) open var runId: String = "",
    @field:Column(name = "source_id", nullable = false) open var sourceId: String = "",
    @field:Column(nullable = false) open var status: String = DiscoveryStatus.PENDING.name,
    @field:Column(columnDefinition = "longtext") open var error: String? = null,
    @field:Column(name = "failed_at") open var failedAt: Instant? = null,
    @field:Column(name = "retry_count", nullable = false) open var retryCount: Int = 0,
    @field:Column(name = "retry_of_source_id") open var retryOfSourceId: String? = null,
    @field:Column(name = "resolved_at") open var resolvedAt: Instant? = null,
    @field:Column(name = "listing_url", nullable = false, length = 2048) open var listingUrl: String = "",
    @field:Column(nullable = false) open var label: String = "",
) {
    fun domain() = RunSource(id, runId, sourceId, DiscoveryStatus.valueOf(status), error, failedAt, retryCount, retryOfSourceId, resolvedAt, listingUrl, label)
}
fun RunSource.entity() = RunSourceEntity(id, runId, sourceId, status.name, error, failedAt, retryCount, retryOfSourceId, resolvedAt, listingUrl, label)

@Entity @Table(name = "crawl_task")
open class CrawlTaskEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "run_id", nullable = false) open var runId: String = "",
    @field:Column(name = "source_id", nullable = false) open var sourceId: String = "",
    @field:Column(nullable = false) open var company: String = "",
    @field:Column(nullable = false, length = 2048) open var url: String = "",
    @field:Column(name = "url_hash", nullable = false, length = 64) open var urlHash: String = "",
    @field:Column(name = "site_key", nullable = false) open var siteKey: String = "",
    @field:Column(name = "discovered_at", nullable = false) open var discoveredAt: Instant = Instant.EPOCH,
    @field:Column(nullable = false, length = 1000) open var title: String = "",
    @field:Column(nullable = false) open var location: String = "",
    @field:Column(nullable = false) open var status: String = TaskStatus.PENDING.name,
    @field:Column(nullable = false) open var stage: String = CrawlStage.BODY.name,
    @field:Column(nullable = false) open var attempts: Int = 0,
    @field:Column(name = "lease_token") open var leaseToken: String? = null,
    @field:Column(name = "lease_expires_at") open var leaseExpiresAt: Instant? = null,
    @field:Column(name = "body_text", columnDefinition = "longtext") open var bodyText: String? = null,
    @field:Column(name = "content_hash", length = 64) open var contentHash: String? = null,
    @field:Column(name = "capture_path", length = 500) open var capturePath: String? = null,
    @field:Column(columnDefinition = "longtext") open var error: String? = null,
    @field:Column(name = "failed_at") open var failedAt: Instant? = null,
    open var outcome: String? = null,
    @field:Column(name = "retry_of_task_id") open var retryOfTaskId: String? = null,
    @field:Column(name = "resolved_at") open var resolvedAt: Instant? = null,
    @field:Column(nullable = false) open var closed: Boolean = false,
) {
    fun domain() = CrawlTask(id, runId, sourceId, company, url, urlHash, siteKey, discoveredAt,
        title, location, TaskStatus.valueOf(status), CrawlStage.valueOf(stage), attempts,
        leaseToken, leaseExpiresAt, bodyText, contentHash, capturePath, error, failedAt,
        outcome?.let(TaskOutcome::valueOf), retryOfTaskId, resolvedAt, closed)
}
fun CrawlTask.entity() = CrawlTaskEntity(id, runId, sourceId, company, url, urlHash, siteKey,
    discoveredAt, title, location, status.name, stage.name, attempts, leaseToken,
    leaseExpiresAt, bodyText, contentHash, capturePath, error, failedAt, outcome?.name,
    retryOfTaskId, resolvedAt, closed)
