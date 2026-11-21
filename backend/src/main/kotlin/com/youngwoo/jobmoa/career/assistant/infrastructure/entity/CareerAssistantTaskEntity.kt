package com.youngwoo.jobmoa.career.assistant.infrastructure.entity

import com.youngwoo.jobmoa.career.assistant.domain.model.*
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "career_assistant_task")
open class CareerAssistantTaskEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "task_type", nullable = false, length = 32) open var taskType: String = CareerAssistantTaskType.RECOMMENDATIONS.name,
    @field:Column(name = "parent_task_id", length = 36) open var parentTaskId: String? = null,
    @field:Column(name = "posting_id") open var postingId: Long? = null,
    @field:Column(nullable = false, length = 24) open var status: String = CareerAssistantTaskStatus.WAITING_AI.name,
    @field:Column(name = "request_json", nullable = false, columnDefinition = "longtext") open var requestJson: String = "{}",
    @field:Column(name = "result_json", columnDefinition = "longtext") open var resultJson: String? = null,
    @field:Column(name = "error_message", columnDefinition = "longtext") open var errorMessage: String? = null,
    @field:Column(nullable = false) open var attempts: Int = 0,
    @field:Column(name = "lease_token", length = 36) open var leaseToken: String? = null,
    @field:Column(name = "lease_expires_at") open var leaseExpiresAt: Instant? = null,
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
    @field:Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.EPOCH,
) {
    fun domain() = CareerAssistantTask(
        id = id,
        type = CareerAssistantTaskType.valueOf(taskType),
        parentTaskId = parentTaskId,
        postingId = postingId,
        status = CareerAssistantTaskStatus.valueOf(status),
        requestJson = requestJson,
        resultJson = resultJson,
        errorMessage = errorMessage,
        attempts = attempts,
        leaseToken = leaseToken,
        leaseExpiresAt = leaseExpiresAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun CareerAssistantTask.entity() = CareerAssistantTaskEntity(
    id = id,
    taskType = type.name,
    parentTaskId = parentTaskId,
    postingId = postingId,
    status = status.name,
    requestJson = requestJson,
    resultJson = resultJson,
    errorMessage = errorMessage,
    attempts = attempts,
    leaseToken = leaseToken,
    leaseExpiresAt = leaseExpiresAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
