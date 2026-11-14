package com.youngwoo.jobmoa.collection.domain.model

import java.time.Instant

enum class RunStatus { DISCOVERING, RUNNING, COMPLETED, PARTIAL_FAILED, FAILED }
enum class DiscoveryStatus { PENDING, RUNNING, COMPLETED, FAILED }

data class CollectionRun(
    val id: String,
    val startedAt: Instant,
    val status: RunStatus = RunStatus.DISCOVERING,
    val finishedAt: Instant? = null,
    val retryOf: String? = null,
    val categoryId: String = "all",
    val companyIds: Set<String> = emptySet(),
) {
    val active: Boolean get() = finishedAt == null
}

data class RunSource(
    val id: String,
    val runId: String,
    val sourceId: String,
    val status: DiscoveryStatus = DiscoveryStatus.PENDING,
    val error: String? = null,
    val failedAt: Instant? = null,
    val retryCount: Int = 0,
    val retryOfSourceId: String? = null,
    val resolvedAt: Instant? = null,
    val listingUrl: String = "",
    val label: String = "",
)
