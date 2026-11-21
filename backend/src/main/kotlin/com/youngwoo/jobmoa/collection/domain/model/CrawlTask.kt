package com.youngwoo.jobmoa.collection.domain.model

import com.youngwoo.jobmoa.shared.LeaseLost
import java.time.Instant

enum class TaskStatus { PENDING, PROCESSING, COMPLETED, FAILED }
enum class CrawlStage { BODY, CAPTURE, SAVE }
enum class TaskOutcome { NEW, CHANGED, IDENTICAL, CLOSED, EXCLUDED }

data class CrawlTask(
    val id: String,
    val runId: String,
    val sourceId: String,
    val company: String,
    val url: String,
    val urlHash: String,
    val siteKey: String,
    val discoveredAt: Instant,
    val title: String = "",
    val location: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val stage: CrawlStage = CrawlStage.BODY,
    val attempts: Int = 0,
    val leaseToken: String? = null,
    val leaseExpiresAt: Instant? = null,
    val bodyText: String? = null,
    val contentHash: String? = null,
    val capturePath: String? = null,
    val error: String? = null,
    val failedAt: Instant? = null,
    val outcome: TaskOutcome? = null,
    val retryOfTaskId: String? = null,
    val resolvedAt: Instant? = null,
    val closed: Boolean = false,
    val categoryId: String = "all",
    val companyId: String = "",
    val reusedVersionId: Long? = null,
) {
    fun requireOwnership(token: String, now: Instant) {
        if (status != TaskStatus.PROCESSING || leaseToken != token || leaseExpiresAt?.isAfter(now) != true) throw LeaseLost()
    }
    fun retry(runId: String, taskId: String): CrawlTask {
        require(status == TaskStatus.FAILED && resolvedAt == null) { "미해결 실패 작업만 재시도할 수 있습니다." }
        return copy(id = taskId, runId = runId, status = TaskStatus.PENDING, attempts = attempts,
            leaseToken = null, leaseExpiresAt = null, error = null, failedAt = null,
            outcome = null, retryOfTaskId = id, resolvedAt = null)
    }
}
