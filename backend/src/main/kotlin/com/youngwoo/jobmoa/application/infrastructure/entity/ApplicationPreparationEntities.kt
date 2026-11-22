package com.youngwoo.jobmoa.application.infrastructure.entity

import com.youngwoo.jobmoa.application.domain.*
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "application_case")
open class ApplicationCaseEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "posting_id", nullable = false, unique = true) open var postingId: Long = 0,
    @field:Column(nullable = false, length = 24) open var status: String = ApplicationCaseStatus.PREPARING.name,
    @field:Column(name = "started_at", nullable = false) open var startedAt: Instant = Instant.EPOCH,
    @field:Column(name = "applied_at") open var appliedAt: Instant? = null,
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
    @field:Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.EPOCH,
) {
    fun domain() = ApplicationCase(id, postingId, ApplicationCaseStatus.valueOf(status), startedAt, appliedAt, createdAt, updatedAt)
}

@Entity
@Table(name = "application_question")
open class ApplicationQuestionEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "application_id", nullable = false, length = 36) open var applicationId: String = "",
    @field:Column(name = "source_requirement_id", length = 36) open var sourceRequirementId: String? = null,
    @field:Column(name = "sort_order", nullable = false) open var sortOrder: Int = 0,
    @field:Column(nullable = false, columnDefinition = "longtext") open var prompt: String = "",
    @field:Column(name = "character_limit") open var characterLimit: Int? = null,
    @field:Column(name = "source_quote", columnDefinition = "longtext") open var sourceQuote: String? = null,
    @field:Column(name = "answer_draft", nullable = false, columnDefinition = "longtext") open var answerDraft: String = "",
    @field:Column(name = "answer_fact_ids_json", nullable = false, columnDefinition = "longtext") open var answerFactIdsJson: String = "[]",
    @field:Column(name = "answer_task_id", length = 36) open var answerTaskId: String? = null,
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
    @field:Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.EPOCH,
) {
    fun domain() = ApplicationQuestion(id, applicationId, sourceRequirementId, sortOrder, prompt, characterLimit, sourceQuote,
        answerDraft, answerFactIdsJson, answerTaskId, createdAt, updatedAt)
}

@Entity
@Table(name = "application_material_draft")
open class ApplicationMaterialsDraftEntity(
    @field:Id @field:Column(name = "application_id", length = 36) open var applicationId: String = "",
    @field:Column(name = "source_task_id", nullable = false, length = 36) open var sourceTaskId: String = "",
    @field:Column(name = "draft_json", nullable = false, columnDefinition = "longtext") open var draftJson: String = "{}",
    @field:Column(nullable = false) open var version: Int = 1,
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
    @field:Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.EPOCH,
) {
    fun domain() = ApplicationMaterialsDraft(applicationId, sourceTaskId, draftJson, version, createdAt, updatedAt)
}

@Entity
@Table(name = "application_interview_session")
open class ApplicationInterviewSessionEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "application_id", nullable = false, length = 36) open var applicationId: String = "",
    @field:Column(nullable = false, length = 24) open var status: String = ApplicationInterviewStatus.ACTIVE.name,
    @field:Column(name = "next_question_index", nullable = false) open var nextQuestionIndex: Int = 0,
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
    @field:Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.EPOCH,
) {
    fun domain() = ApplicationInterviewSession(id, applicationId, ApplicationInterviewStatus.valueOf(status), nextQuestionIndex, createdAt, updatedAt)
}

@Entity
@Table(name = "application_interview_turn")
open class ApplicationInterviewTurnEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "session_id", nullable = false, length = 36) open var sessionId: String = "",
    @field:Column(nullable = false) open var position: Int = 0,
    @field:Column(nullable = false, length = 32) open var basis: String = "POSTING",
    @field:Column(nullable = false, columnDefinition = "longtext") open var question: String = "",
    @field:Column(nullable = false, columnDefinition = "longtext") open var answer: String = "",
    @field:Column(name = "follow_up_question", columnDefinition = "longtext") open var followUpQuestion: String? = null,
    @field:Column(name = "follow_up_answer", nullable = false, columnDefinition = "longtext") open var followUpAnswer: String = "",
    @field:Column(name = "proposed_title", length = 500) open var proposedTitle: String? = null,
    @field:Column(name = "proposed_summary", columnDefinition = "longtext") open var proposedSummary: String? = null,
    @field:Column(name = "source_excerpt", length = 1000) open var sourceExcerpt: String? = null,
    @field:Column(name = "promoted_material_id", length = 36) open var promotedMaterialId: String? = null,
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
    @field:Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.EPOCH,
) {
    fun domain() = ApplicationInterviewTurn(id, sessionId, position, basis, question, answer, followUpQuestion, followUpAnswer,
        proposedTitle, proposedSummary, sourceExcerpt, promotedMaterialId, createdAt, updatedAt)
}

@Entity
@Table(name = "application_ai_task")
open class ApplicationAiTaskEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "application_id", nullable = false, length = 36) open var applicationId: String = "",
    @field:Column(name = "posting_id", nullable = false) open var postingId: Long = 0,
    @field:Column(name = "task_type", nullable = false, length = 32) open var taskType: String = ApplicationAiTaskType.EXTRACT_QUESTIONS.name,
    @field:Column(nullable = false, length = 24) open var status: String = ApplicationAiTaskStatus.WAITING_AI.name,
    @field:Column(name = "request_json", nullable = false, columnDefinition = "longtext") open var requestJson: String = "{}",
    @field:Column(name = "result_json", columnDefinition = "longtext") open var resultJson: String? = null,
    @field:Column(name = "error_message", columnDefinition = "longtext") open var errorMessage: String? = null,
    @field:Column(name = "progress_message", length = 255) open var progressMessage: String? = null,
    @field:Column(nullable = false) open var attempts: Int = 0,
    @field:Column(name = "lease_token", length = 36) open var leaseToken: String? = null,
    @field:Column(name = "lease_expires_at") open var leaseExpiresAt: Instant? = null,
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
    @field:Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.EPOCH,
) {
    fun domain() = ApplicationAiTask(id, applicationId, postingId, ApplicationAiTaskType.valueOf(taskType), ApplicationAiTaskStatus.valueOf(status),
        requestJson, resultJson, errorMessage, progressMessage, attempts, leaseToken, leaseExpiresAt, createdAt, updatedAt)
}

fun ApplicationCase.entity() = ApplicationCaseEntity(id, postingId, status.name, startedAt, appliedAt, createdAt, updatedAt)
fun ApplicationQuestion.entity() = ApplicationQuestionEntity(id, applicationId, sourceRequirementId, position, prompt, characterLimit, sourceQuote,
    answerDraft, answerFactIdsJson, answerTaskId, createdAt, updatedAt)
fun ApplicationMaterialsDraft.entity() = ApplicationMaterialsDraftEntity(applicationId, sourceTaskId, draftJson, version, createdAt, updatedAt)
fun ApplicationInterviewSession.entity() = ApplicationInterviewSessionEntity(id, applicationId, status.name, nextQuestionIndex, createdAt, updatedAt)
fun ApplicationInterviewTurn.entity() = ApplicationInterviewTurnEntity(id, sessionId, position, basis, question, answer, followUpQuestion, followUpAnswer,
    proposedTitle, proposedSummary, sourceExcerpt, promotedMaterialId, createdAt, updatedAt)
fun ApplicationAiTask.entity() = ApplicationAiTaskEntity(id, applicationId, postingId, type.name, status.name, requestJson, resultJson, errorMessage, progressMessage,
    attempts, leaseToken, leaseExpiresAt, createdAt, updatedAt)
