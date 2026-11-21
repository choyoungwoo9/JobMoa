package com.youngwoo.jobmoa.aiqa.application

import com.youngwoo.jobmoa.aiqa.domain.model.*
import com.youngwoo.jobmoa.aiqa.domain.repository.AiChatRepository
import com.youngwoo.jobmoa.shared.LeaseLost
import com.youngwoo.jobmoa.shared.ResourceNotFound
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class AiChatService(
    private val repository: AiChatRepository,
    private val knowledge: AiKnowledgeService,
    private val presence: AiWorkerPresence,
    private val eventHub: AiTaskEventHub,
    private val mapper: JsonMapper,
) {
    fun workerStatus() = presence.status()

    @Transactional(readOnly = true)
    fun conversations() = repository.conversations()

    @Transactional
    fun createConversation(): AiConversation {
        val now = Instant.now()
        val conversation = AiConversation(UUID.randomUUID().toString(), "새 대화", now, now)
        repository.saveConversation(conversation)
        return conversation
    }

    @Transactional(readOnly = true)
    fun conversation(id: String): Map<String, Any> {
        val conversation = repository.findConversation(id) ?: throw ResourceNotFound("대화를 찾을 수 없습니다.")
        return mapOf("conversation" to conversation, "messages" to repository.messages(id))
    }

    @Transactional
    fun deleteConversation(id: String) {
        if (repository.findConversation(id) == null) throw ResourceNotFound("대화를 찾을 수 없습니다.")
        repository.deleteConversation(id)
    }

    @Transactional
    fun sendMessage(conversationId: String, content: String): Map<String, Any> {
        val text = content.trim()
        require(text.isNotEmpty()) { "질문을 입력하세요." }
        require(text.length <= MAX_QUESTION_CHARS) { "질문은 ${MAX_QUESTION_CHARS}자까지 입력할 수 있습니다." }
        val conversation = repository.lockConversation(conversationId) ?: throw ResourceNotFound("대화를 찾을 수 없습니다.")
        val now = Instant.now()
        val userMessage = AiMessage(UUID.randomUUID().toString(), conversationId, AiMessageRole.USER,
            AiMessageStatus.COMPLETE, text, createdAt = now, updatedAt = now)
        val assistantMessage = AiMessage(UUID.randomUUID().toString(), conversationId, AiMessageRole.ASSISTANT,
            AiMessageStatus.QUEUED, "", createdAt = now, updatedAt = now)
        val task = AiTask(UUID.randomUUID().toString(), conversationId, userMessage.id, assistantMessage.id,
            AiTaskStatus.WAITING, 0, createdAt = now, updatedAt = now)
        repository.saveMessage(userMessage)
        repository.saveMessage(assistantMessage)
        repository.saveTask(task)
        repository.saveConversation(conversation.copy(
            title = if (conversation.title == "새 대화") text.take(TITLE_MAX_CHARS) else conversation.title,
            updatedAt = now,
        ))
        return mapOf("taskId" to task.id, "messages" to listOf(userMessage, assistantMessage.copy(taskId = task.id)))
    }

    @Transactional
    fun retry(assistantMessageId: String): AiMessage {
        val assistant = repository.findMessage(assistantMessageId) ?: throw ResourceNotFound("답변을 찾을 수 없습니다.")
        require(assistant.role == AiMessageRole.ASSISTANT && assistant.status == AiMessageStatus.FAILED) {
            "실패한 답변만 다시 요청할 수 있습니다."
        }
        val task = repository.taskForAssistantMessage(assistantMessageId) ?: throw ResourceNotFound("AI 작업을 찾을 수 없습니다.")
        val now = Instant.now()
        repository.saveTask(task.copy(status = AiTaskStatus.WAITING, leaseToken = null, leaseExpiresAt = null,
            postingContextJson = "[]", searchPerformed = false, errorMessage = null, updatedAt = now))
        val queued = assistant.copy(status = AiMessageStatus.QUEUED, content = "", errorMessage = null, updatedAt = now,
            citations = emptyList(), taskId = task.id, lastEventId = task.lastEventId, searchPerformed = false)
        repository.saveMessage(queued)
        repository.saveCitations(assistantMessageId, emptyList(), now)
        repository.findConversation(assistant.conversationId)?.let {
            repository.saveConversation(it.copy(updatedAt = now))
        }
        return queued
    }

    @Transactional
    fun heartbeat(workerId: String, model: String) = presence.heartbeat(workerId, model)

    @Transactional
    fun claim(): AiWorkItem? {
        presence.requireConnected()
        val now = Instant.now()
        val task = repository.claimNextTask(now, TASK_LEASE) ?: return null
        val userMessage = repository.findMessage(task.userMessageId) ?: throw ResourceNotFound("질문을 찾을 수 없습니다.")
        val previous = repository.recentMessages(task.conversationId, HISTORY_LIMIT + 2)
            .filter { it.id != task.userMessageId && it.id != task.assistantMessageId }
            .filter { it.role == AiMessageRole.USER || it.status == AiMessageStatus.COMPLETE }
            .takeLast(HISTORY_LIMIT)
        val context = knowledge.context(userMessage.content, previous)
        val updatedTask = task.copy(postingContextJson = mapper.writeValueAsString(context.postings), updatedAt = now)
        repository.saveTask(updatedTask)
        val event = repository.appendTaskEvent(task.id, "status", mapper.writeValueAsString(mapOf(
            "message" to "공고 자료를 확인하고 답변을 준비하고 있어요.",
            "resetAnswer" to "true",
        )), now)
        repository.saveTask(updatedTask.copy(lastEventId = event.id))
        publishAfterCommit(event)
        return AiWorkItem(task.id, checkNotNull(task.leaseToken), task.conversationId,
            task.userMessageId, task.assistantMessageId, userMessage.content, context)
    }

    @Transactional
    fun renew(taskId: String, leaseToken: String): Boolean =
        repository.renewTask(taskId, leaseToken, Instant.now(), TASK_LEASE)

    @Transactional
    fun progress(taskId: String, leaseToken: String, type: String, payload: Map<String, String>) {
        require(type in setOf("status", "search", "delta")) { "지원하지 않는 AI 작업 이벤트입니다." }
        val now = Instant.now()
        val task = repository.lockTask(taskId) ?: throw ResourceNotFound("AI 작업을 찾을 수 없습니다.")
        requireLease(task, leaseToken, now)
        if (type == "delta") {
            val text = payload["text"].orEmpty()
            require(text.isNotEmpty()) { "빈 답변 조각은 저장할 수 없습니다." }
            val assistant = repository.findMessage(task.assistantMessageId) ?: throw ResourceNotFound("답변을 찾을 수 없습니다.")
            val accumulated = assistant.content + text
            require(accumulated.length <= MAX_ANSWER_CHARS) { "AI 답변이 허용 길이를 초과했습니다." }
            repository.saveMessage(assistant.copy(status = AiMessageStatus.PROCESSING, content = accumulated, updatedAt = now))
        }
        val event = repository.appendTaskEvent(taskId, type, mapper.writeValueAsString(payload), now)
        repository.saveTask(task.copy(lastEventId = event.id, searchPerformed = task.searchPerformed || type == "search", updatedAt = now))
        publishAfterCommit(event)
    }

    @Transactional(readOnly = true)
    fun events(taskId: String, afterId: Long): SseEmitter {
        require(afterId >= 0) { "이벤트 ID는 0 이상이어야 합니다." }
        val task = repository.findTask(taskId) ?: throw ResourceNotFound("AI 작업을 찾을 수 없습니다.")
        return eventHub.subscribe(taskId, afterId, task.status in setOf(AiTaskStatus.COMPLETED, AiTaskStatus.FAILED))
    }

    @Transactional
    fun complete(taskId: String, leaseToken: String, result: AiWorkerResult) {
        val now = Instant.now()
        val task = repository.lockTask(taskId) ?: throw ResourceNotFound("AI 작업을 찾을 수 없습니다.")
        requireLease(task, leaseToken, now)
        val assistant = repository.findMessage(task.assistantMessageId) ?: throw ResourceNotFound("답변을 찾을 수 없습니다.")
        val postingContexts = knowledge.citations(task).associateBy { it.id }
        val citations = buildList {
            result.postingCitationIds.distinct().take(MAX_CITATIONS).forEach { id ->
                postingContexts[id]?.let { posting ->
                    add(AiCitation(AiCitationType.POSTING, posting.id, posting.versionId, posting.company,
                        posting.title, posting.url, "${posting.location}${if (posting.closed) " · 마감" else " · 진행 중"}", size))
                }
            }
            result.webCitations.distinctBy { it.url }.take(MAX_WEB_CITATIONS).forEach { citation ->
                val checked = checkedWebCitation(citation)
                add(checked.copy(position = size))
            }
            if (result.workspaceUsed && isEmpty()) {
                add(AiCitation(AiCitationType.WORKSPACE, title = "JobMoa 저장 공고 데이터",
                    reason = "답변을 생성할 때 저장된 공고와 직군 정보를 조회했습니다.", position = size))
            }
        }
        val answer = result.answer.trim()
        require(answer.isNotEmpty() && answer.length <= MAX_ANSWER_CHARS) { "AI 답변이 비어 있거나 허용 길이를 초과했습니다." }
        val searched = result.searchPerformed || citations.any { it.type == AiCitationType.WEB }
        repository.saveMessage(assistant.copy(status = AiMessageStatus.COMPLETE, content = answer,
            errorMessage = null, updatedAt = now, citations = citations, taskId = task.id,
            searchPerformed = task.searchPerformed || searched))
        repository.saveCitations(assistant.id, citations, now)
        repository.saveTask(task.copy(status = AiTaskStatus.COMPLETED, leaseToken = null, leaseExpiresAt = null,
            searchPerformed = searched, errorMessage = null, updatedAt = now))
        val event = repository.appendTaskEvent(task.id, "complete", mapper.writeValueAsString(mapOf(
            "messageId" to assistant.id, "searchPerformed" to searched,
        )), now)
        repository.saveTask(task.copy(status = AiTaskStatus.COMPLETED, leaseToken = null, leaseExpiresAt = null,
            searchPerformed = searched, lastEventId = event.id, errorMessage = null, updatedAt = now))
        publishAfterCommit(event)
        repository.findConversation(task.conversationId)?.let { repository.saveConversation(it.copy(updatedAt = now)) }
    }

    @Transactional
    fun fail(taskId: String, leaseToken: String, error: String) {
        val now = Instant.now()
        val task = repository.lockTask(taskId) ?: throw ResourceNotFound("AI 작업을 찾을 수 없습니다.")
        requireLease(task, leaseToken, now)
        val message = safeError(error)
        repository.saveTask(task.copy(status = AiTaskStatus.FAILED, leaseToken = null, leaseExpiresAt = null,
            errorMessage = message, updatedAt = now))
        repository.findMessage(task.assistantMessageId)?.let { assistant ->
            repository.saveMessage(assistant.copy(status = AiMessageStatus.FAILED, errorMessage = message, updatedAt = now))
        }
        val event = repository.appendTaskEvent(task.id, "failed", mapper.writeValueAsString(mapOf("message" to message)), now)
        repository.saveTask(task.copy(status = AiTaskStatus.FAILED, leaseToken = null, leaseExpiresAt = null,
            errorMessage = message, lastEventId = event.id, updatedAt = now))
        publishAfterCommit(event)
        repository.findConversation(task.conversationId)?.let { repository.saveConversation(it.copy(updatedAt = now)) }
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    @Transactional
    fun recoverExpiredTasks() {
        repository.recoverExpiredTasks(Instant.now()).forEach(::publishAfterCommit)
    }

    private fun requireLease(task: AiTask, token: String, now: Instant) {
        if (task.status != AiTaskStatus.PROCESSING || task.leaseToken != token || task.leaseExpiresAt?.isAfter(now) != true) {
            throw LeaseLost()
        }
    }

    private fun publishAfterCommit(event: com.youngwoo.jobmoa.aiqa.domain.model.AiTaskEvent) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = eventHub.publish(event)
            })
        } else eventHub.publish(event)
    }

    private fun checkedWebCitation(citation: AiCitation): AiCitation {
        require(citation.type == AiCitationType.WEB) { "웹 출처 형식이 올바르지 않습니다." }
        require(citation.title.isNotBlank() && citation.title.length <= 1000) { "웹 출처 제목이 올바르지 않습니다." }
        val uri = runCatching { URI(citation.url ?: "") }.getOrNull()
        require(uri != null && uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "웹 출처 주소는 공개 HTTP 또는 HTTPS 주소여야 합니다."
        }
        val host = uri.host.lowercase()
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local") && !host.endsWith(".internal")) {
            "로컬 주소는 웹 출처로 저장할 수 없습니다."
        }
        return citation.copy(title = citation.title.take(1000), url = uri.toASCIIString(),
            reason = citation.reason?.take(1000), position = 0)
    }

    private fun safeError(error: String): String = error
        .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~-]+"), "Bearer [숨김]")
        .replace(Regex("sk-[A-Za-z0-9_-]{12,}"), "sk-[숨김]")
        .trim().take(MAX_ERROR_CHARS).ifBlank { "Codex CLI가 오류를 반환했습니다." }

    companion object {
        private const val TITLE_MAX_CHARS = 80
        private const val MAX_QUESTION_CHARS = 6000
        private const val MAX_ANSWER_CHARS = 30000
        private const val MAX_ERROR_CHARS = 2000
        private const val MAX_CITATIONS = 20
        private const val MAX_WEB_CITATIONS = 10
        private const val HISTORY_LIMIT = 10
        private val TASK_LEASE = Duration.ofMinutes(5)
    }
}
