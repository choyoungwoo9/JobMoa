package com.youngwoo.jobmoa.collection.domain.model

import java.time.Instant

data class CollectionChange(val taskId: String, val runId: String, val postingId: Long,
    val outcome: String, val wasStarred: Boolean?, val becameClosed: Boolean?, val recordedAt: Instant)
interface CollectionChangeRepository {
    fun append(change: CollectionChange)
    fun list(runId: String): List<CollectionChange>
}
