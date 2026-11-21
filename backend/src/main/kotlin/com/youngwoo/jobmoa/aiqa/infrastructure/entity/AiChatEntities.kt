package com.youngwoo.jobmoa.aiqa.infrastructure.entity

import com.youngwoo.jobmoa.aiqa.domain.model.*
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "ai_conversation")
open class AiConversationEntity(
    @field:Id open var id: String = "",
    @field:Column(nullable = false, length = 200) open var title: String = "",
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
    @field:Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.EPOCH,
) {
    fun domain() = AiConversation(id, title, createdAt, updatedAt)
}

@Entity
@Table(name = "ai_message")
open class AiMessageEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "conversation_id", nullable = false) open var conversationId: String = "",
    @field:Column(nullable = false, length = 16) open var role: String = AiMessageRole.USER.name,
    @field:Column(nullable = false, length = 16) open var status: String = AiMessageStatus.COMPLETE.name,
    @field:Column(nullable = false, columnDefinition = "longtext") open var content: String = "",
    @field:Column(name = "error_message", length = 2000) open var errorMessage: String? = null,
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
    @field:Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.EPOCH,
) {
    fun domain(
        citations: List<AiCitation> = emptyList(),
        taskId: String? = null,
        lastEventId: Long = 0,
        searchPerformed: Boolean = false,
    ) = AiMessage(
        id, conversationId, AiMessageRole.valueOf(role), AiMessageStatus.valueOf(status), content,
        errorMessage, createdAt, updatedAt, citations, taskId, lastEventId, searchPerformed,
    )
}

@Entity
@Table(name = "ai_task")
open class AiTaskEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "conversation_id", nullable = false) open var conversationId: String = "",
    @field:Column(name = "user_message_id", nullable = false) open var userMessageId: String = "",
    @field:Column(name = "assistant_message_id", nullable = false) open var assistantMessageId: String = "",
    @field:Column(nullable = false, length = 16) open var status: String = AiTaskStatus.WAITING.name,
    @field:Column(nullable = false) open var attempts: Int = 0,
    @field:Column(name = "lease_token") open var leaseToken: String? = null,
    @field:Column(name = "lease_expires_at") open var leaseExpiresAt: Instant? = null,
    @field:Column(name = "posting_context_json", nullable = false, columnDefinition = "longtext") open var postingContextJson: String = "[]",
    @field:Column(name = "search_performed", nullable = false) open var searchPerformed: Boolean = false,
    @field:Column(name = "last_event_id", nullable = false) open var lastEventId: Long = 0,
    @field:Column(name = "error_message", columnDefinition = "longtext") open var errorMessage: String? = null,
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
    @field:Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.EPOCH,
) {
    fun domain() = AiTask(id, conversationId, userMessageId, assistantMessageId,
        AiTaskStatus.valueOf(status), attempts, leaseToken, leaseExpiresAt, postingContextJson,
        searchPerformed, lastEventId, errorMessage, createdAt, updatedAt)
}

@Entity
@Table(name = "ai_task_event")
open class AiTaskEventEntity(
    @field:Id @field:GeneratedValue(strategy = GenerationType.IDENTITY) open var id: Long? = null,
    @field:Column(name = "task_id", nullable = false) open var taskId: String = "",
    @field:Column(name = "event_type", nullable = false, length = 24) open var type: String = "status",
    @field:Column(name = "payload_json", nullable = false, columnDefinition = "longtext") open var payloadJson: String = "{}",
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
) {
    fun domain() = AiTaskEvent(checkNotNull(id), taskId, type, payloadJson, createdAt)
}

@Entity
@Table(name = "ai_citation")
open class AiCitationEntity(
    @field:Id @field:GeneratedValue(strategy = GenerationType.IDENTITY) open var id: Long? = null,
    @field:Column(name = "message_id", nullable = false) open var messageId: String = "",
    @field:Column(nullable = false) open var position: Int = 0,
    @field:Column(name = "source_type", nullable = false, length = 24) open var sourceType: String = AiCitationType.POSTING.name,
    @field:Column(name = "posting_id") open var postingId: Long? = null,
    @field:Column(name = "version_id") open var versionId: Long? = null,
    @field:Column(length = 255) open var company: String? = null,
    @field:Column(nullable = false, length = 1000) open var title: String = "",
    @field:Column(length = 2048) open var url: String? = null,
    @field:Column(length = 1000) open var reason: String? = null,
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
) {
    fun domain() = AiCitation(AiCitationType.valueOf(sourceType), postingId, versionId, company, title, url, reason, position)
}

fun AiConversation.entity() = AiConversationEntity(id, title, createdAt, updatedAt)
fun AiMessage.entity() = AiMessageEntity(id, conversationId, role.name, status.name, content, errorMessage, createdAt, updatedAt)
fun AiTask.entity() = AiTaskEntity(id, conversationId, userMessageId, assistantMessageId, status.name, attempts,
    leaseToken, leaseExpiresAt, postingContextJson, searchPerformed, lastEventId, errorMessage, createdAt, updatedAt)
fun AiCitation.entity(messageId: String, now: Instant) = AiCitationEntity(
    messageId = messageId, position = position, sourceType = type.name, postingId = postingId,
    versionId = versionId, company = company, title = title, url = url, reason = reason, createdAt = now,
)
