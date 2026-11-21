package com.youngwoo.jobmoa.aiqa.infrastructure.repository

import com.youngwoo.jobmoa.aiqa.domain.model.*
import com.youngwoo.jobmoa.aiqa.domain.repository.AiChatRepository
import com.youngwoo.jobmoa.aiqa.infrastructure.entity.*
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Repository
@Transactional
class AiChatRepositoryImpl(
    private val conversations: AiConversationJpaRepository,
    private val messages: AiMessageJpaRepository,
    private val tasks: AiTaskJpaRepository,
    private val citations: AiCitationJpaRepository,
    private val events: AiTaskEventJpaRepository,
    private val postings: AiPostingContextJpaRepository,
    private val mapper: JsonMapper,
) : AiChatRepository {
    override fun conversations(): List<AiConversationPreview> = conversations.previews().map {
        AiConversationPreview(it.id, it.title, it.updatedAt, it.preview)
    }

    override fun findConversation(id: String): AiConversation? = conversations.findById(id).orElse(null)?.domain()
    override fun lockConversation(id: String): AiConversation? = conversations.lockById(id)?.domain()
    override fun saveConversation(conversation: AiConversation) { conversations.save(conversation.entity()) }
    override fun deleteConversation(id: String) { conversations.deleteById(id) }

    override fun messages(conversationId: String): List<AiMessage> =
        messages.findAllByConversationIdOrderByCreatedAtAscIdAsc(conversationId).map(::withCitations)

    override fun recentMessages(conversationId: String, limit: Int): List<AiMessage> =
        messages.findAllByConversationIdOrderByCreatedAtAscIdAsc(conversationId).takeLast(limit).map(::withCitations)

    override fun findMessage(id: String): AiMessage? = messages.findById(id).orElse(null)?.let(::withCitations)
    override fun saveMessage(message: AiMessage) { messages.save(message.entity()) }

    override fun saveCitations(messageId: String, values: List<AiCitation>, now: Instant) {
        citations.deleteAllByMessageId(messageId)
        if (values.isNotEmpty()) citations.saveAll(values.map { it.entity(messageId, now) })
    }

    override fun claimNextTask(now: Instant, lease: Duration): AiTask? {
        val task = tasks.nextForUpdate() ?: return null
        task.status = AiTaskStatus.PROCESSING.name
        task.attempts += 1
        task.leaseToken = UUID.randomUUID().toString()
        task.leaseExpiresAt = now.plus(lease)
        task.updatedAt = now
        val assistant = messages.lockById(task.assistantMessageId)
        assistant?.let { it.status = AiMessageStatus.PROCESSING.name; it.updatedAt = now }
        return task.domain()
    }

    override fun findTask(id: String): AiTask? = tasks.findById(id).orElse(null)?.domain()
    override fun lockTask(id: String): AiTask? = tasks.lockById(id)?.domain()
    override fun taskForAssistantMessage(messageId: String): AiTask? = tasks.findFirstByAssistantMessageId(messageId)?.domain()
    override fun saveTask(task: AiTask) { tasks.save(task.entity()) }

    override fun renewTask(id: String, leaseToken: String, now: Instant, lease: Duration): Boolean =
        tasks.renew(id, leaseToken, now, now.plus(lease)) == 1

    override fun recoverExpiredTasks(now: Instant): List<AiTaskEvent> {
        val expired = tasks.findAllByStatusAndLeaseExpiresAtBefore(AiTaskStatus.PROCESSING.name, now)
        return expired.map { task ->
            task.status = AiTaskStatus.WAITING.name
            task.leaseToken = null
            task.leaseExpiresAt = null
            task.errorMessage = null
            task.updatedAt = now
            messages.findById(task.assistantMessageId).orElse(null)?.let { message ->
                message.status = AiMessageStatus.QUEUED.name
                message.content = ""
                message.errorMessage = null
                message.updatedAt = now
            }
            appendTaskEvent(task.id!!, "status", mapper.writeValueAsString(mapOf(
                "message" to "워커 연결이 끊겨 작업을 다시 대기 중입니다.",
                "resetAnswer" to "true",
            )), now)
        }
    }

    override fun postingIndex(): List<AiPostingContext> = postings.allContexts().map(::context)
    override fun postingContexts(ids: List<Long>): List<AiPostingContext> =
        if (ids.isEmpty()) emptyList() else postings.contextsByIds(ids.distinct()).map(::context)

    private fun withCitations(message: AiMessageEntity): AiMessage {
        val task = if (message.role == AiMessageRole.ASSISTANT.name) tasks.findFirstByAssistantMessageId(message.id!!) else null
        return message.domain(
            citations.findAllByMessageIdOrderByPositionAsc(message.id!!).map { it.domain() },
            task?.id, task?.lastEventId ?: 0, task?.searchPerformed ?: false,
        )
    }

    override fun appendTaskEvent(taskId: String, type: String, payloadJson: String, now: Instant): AiTaskEvent {
        val event = events.saveAndFlush(AiTaskEventEntity(taskId = taskId, type = type, payloadJson = payloadJson, createdAt = now))
        tasks.findById(taskId).orElseThrow().also {
            it.lastEventId = checkNotNull(event.id)
            if (type == "search") it.searchPerformed = true
            it.updatedAt = now
        }
        return event.domain()
    }

    override fun taskEventsAfter(taskId: String, afterId: Long): List<AiTaskEvent> =
        events.findAllByTaskIdAndIdGreaterThanOrderByIdAsc(taskId, afterId).map { it.domain() }

    private fun context(row: AiPostingContextProjection) = AiPostingContext(
        row.id, row.sourceId, row.company, row.title, row.location, row.url,
        row.firstSeen, row.closed, row.read,
        row.categoryIds.split(',').filter(String::isNotBlank),
        row.versionId, row.versionSavedAt, row.body.orEmpty(),
    )
}
