package com.youngwoo.jobmoa.collection.domain.model

import java.time.Instant

/** 진행률 조회에는 본문과 캡처 체크포인트를 읽지 않는다. */
data class TaskSummary(
    val id: String, val sourceId: String, val company: String, val title: String,
    val url: String, val urlHash: String, val status: TaskStatus, val stage: CrawlStage,
    val attempts: Int, val failedAt: Instant?, val error: String?, val outcome: TaskOutcome?,
    val resolvedAt: Instant?, val retryOfTaskId: String?,
    val categoryId: String = "all", val companyId: String = "", val reusedVersionId: Long? = null,
)
