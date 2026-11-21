package com.youngwoo.jobmoa.aiqa.domain.model

import java.time.Instant

enum class AiMessageRole { USER, ASSISTANT }
enum class AiMessageStatus { COMPLETE, QUEUED, PROCESSING, FAILED }
enum class AiTaskStatus { WAITING, PROCESSING, COMPLETED, FAILED }
enum class AiCitationType { POSTING, WEB, WORKSPACE }

data class AiConversation(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AiMessage(
    val id: String,
    val conversationId: String,
    val role: AiMessageRole,
    val status: AiMessageStatus,
    val content: String,
    val errorMessage: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val citations: List<AiCitation> = emptyList(),
    val taskId: String? = null,
    val lastEventId: Long = 0,
    val searchPerformed: Boolean = false,
)

data class AiCitation(
    val type: AiCitationType,
    val postingId: Long? = null,
    val versionId: Long? = null,
    val company: String? = null,
    val title: String,
    val url: String? = null,
    val reason: String? = null,
    val position: Int = 0,
)

data class AiTask(
    val id: String,
    val conversationId: String,
    val userMessageId: String,
    val assistantMessageId: String,
    val status: AiTaskStatus,
    val attempts: Int,
    val leaseToken: String? = null,
    val leaseExpiresAt: Instant? = null,
    val postingContextJson: String = "[]",
    val searchPerformed: Boolean = false,
    val lastEventId: Long = 0,
    val errorMessage: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AiTaskEvent(
    val id: Long,
    val taskId: String,
    val type: String,
    val payloadJson: String,
    val createdAt: Instant,
)

data class AiPostingContext(
    val id: Long,
    val sourceId: String,
    val company: String,
    val title: String,
    val location: String,
    val url: String,
    val firstSeen: Instant,
    val closed: Boolean,
    val read: Boolean,
    val categoryIds: List<String>,
    val versionId: Long?,
    val versionSavedAt: Instant?,
    val body: String,
)

data class AiWorkspaceSummary(
    val total: Int,
    val open: Int,
    val closed: Int,
    val unclassified: Int,
    val byCompany: Map<String, Map<String, Int>>,
    val byCategory: Map<String, Map<String, Int>>,
)

data class AiChatContext(
    val summary: AiWorkspaceSummary,
    val postings: List<AiPostingContext>,
    val candidateCount: Int,
    val postingsTruncated: Boolean,
    val history: List<AiMessage>,
)

data class AiWorkerStatus(
    val connected: Boolean,
    val workerId: String? = null,
    val model: String = "gpt-6-luna",
    val webSearch: Boolean = true,
    val lastSeenAt: Instant? = null,
)

data class AiConversationPreview(
    val id: String,
    val title: String,
    val updatedAt: Instant,
    val preview: String,
)

data class AiWorkItem(
    val taskId: String,
    val leaseToken: String,
    val conversationId: String,
    val userMessageId: String,
    val assistantMessageId: String,
    val question: String,
    val context: AiChatContext,
)

data class AiWorkerResult(
    val answer: String,
    val postingCitationIds: List<Long>,
    val webCitations: List<AiCitation>,
    val searchPerformed: Boolean,
    val workspaceUsed: Boolean,
)
