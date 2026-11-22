package com.youngwoo.jobmoa.application.domain

import java.time.Instant

enum class ApplicationCaseStatus { PREPARING, APPLIED }
enum class ApplicationAiTaskType { MATERIAL_DRAFT, EXTRACT_QUESTIONS, DRAFT_ANSWER, INTERVIEW_RESEARCH, INTERVIEW_FOLLOW_UP }
enum class ApplicationAiTaskStatus { WAITING_AI, ANALYZING, COMPLETED, FAILED }
enum class ApplicationInterviewStatus { ACTIVE, COMPLETED }

data class ApplicationCase(
    val id: String,
    val postingId: Long,
    val status: ApplicationCaseStatus,
    val startedAt: Instant,
    val appliedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ApplicationQuestion(
    val id: String,
    val applicationId: String,
    val sourceRequirementId: String?,
    val position: Int,
    val prompt: String,
    val characterLimit: Int?,
    val sourceQuote: String?,
    val answerDraft: String,
    val answerFactIdsJson: String,
    val answerTaskId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ApplicationMaterialsDraft(
    val applicationId: String,
    val sourceTaskId: String,
    val draftJson: String,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ApplicationInterviewSession(
    val id: String,
    val applicationId: String,
    val status: ApplicationInterviewStatus,
    val nextQuestionIndex: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ApplicationInterviewTurn(
    val id: String,
    val sessionId: String,
    val position: Int,
    val basis: String,
    val question: String,
    val answer: String,
    val followUpQuestion: String?,
    val followUpAnswer: String,
    val proposedTitle: String?,
    val proposedSummary: String?,
    val sourceExcerpt: String?,
    val promotedMaterialId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ApplicationAiTask(
    val id: String,
    val applicationId: String,
    val postingId: Long,
    val type: ApplicationAiTaskType,
    val status: ApplicationAiTaskStatus,
    val requestJson: String,
    val resultJson: String?,
    val errorMessage: String?,
    val progressMessage: String?,
    val attempts: Int,
    val leaseToken: String?,
    val leaseExpiresAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ApplicationAiTaskLease(val task: ApplicationAiTask, val token: String)
