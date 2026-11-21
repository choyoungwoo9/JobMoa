package com.youngwoo.jobmoa.applicationrequirements.domain

import java.time.Instant

enum class RequirementSource { POSTING_BODY, USER_PASTED }
enum class RequirementStatus { WAITING_AI, ANALYZING, REVIEW, CONFIRMED, FAILED }

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
)

data class ApplicationRequirementAnalysis(val resultJson: String)
