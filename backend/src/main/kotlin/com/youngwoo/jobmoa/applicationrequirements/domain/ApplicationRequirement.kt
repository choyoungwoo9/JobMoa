package com.youngwoo.jobmoa.applicationrequirements.domain

import java.time.Instant

enum class RequirementSource { POSTING_BODY, USER_PASTED, PUBLIC_PAGE }
enum class RequirementStatus { WAITING_PUBLIC, FETCHING_PUBLIC, WAITING_AI, ANALYZING, REVIEW, CONFIRMED, FAILED }

data class ApplicationRequirement(
    val id: String,
    val postingId: Long,
    val source: RequirementSource,
    val sourceUrl: String?,
    val rawText: String,
    val resultJson: String?,
    val status: RequirementStatus,
    val attempts: Int,
    val errorMessage: String?,
    val leaseToken: String?,
    val leaseExpiresAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val confirmedAt: Instant?,
    val collectionRunId: String? = null,
    val publicPageFetched: Boolean = false,
)

data class ApplicationRequirementAnalysis(val resultJson: String)

data class PublicPageFetchLease(
    val id: String,
    val postingId: Long,
    val leaseToken: String,
    val postingUrl: String,
)

data class ApplicationRequirementStatusCount(val status: RequirementStatus, val publicPageFetched: Boolean, val total: Int)

data class ApplicationRequirementRunSummary(
    val postingId: Long,
    val sourceId: String,
    val company: String,
    val url: String,
    val status: RequirementStatus,
    val publicPageFetched: Boolean,
    val errorMessage: String?,
)
