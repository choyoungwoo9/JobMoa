package com.youngwoo.jobmoa.aiqa.domain.repository

import com.youngwoo.jobmoa.aiqa.domain.model.*
import java.time.Duration
import java.time.Instant

interface AiChatRepository {
    fun conversations(): List<AiConversationPreview>
    fun findConversation(id: String): AiConversation?
    fun lockConversation(id: String): AiConversation?
    fun saveConversation(conversation: AiConversation)
    fun deleteConversation(id: String)
    fun messages(conversationId: String): List<AiMessage>
    fun recentMessages(conversationId: String, limit: Int): List<AiMessage>
    fun findMessage(id: String): AiMessage?
    fun saveMessage(message: AiMessage)
    fun saveCitations(messageId: String, citations: List<AiCitation>, now: Instant)
    fun claimNextTask(now: Instant, lease: Duration): AiTask?
    fun findTask(id: String): AiTask?
    fun lockTask(id: String): AiTask?
    fun taskForAssistantMessage(messageId: String): AiTask?
    fun saveTask(task: AiTask)
    fun renewTask(id: String, leaseToken: String, now: Instant, lease: Duration): Boolean
    fun recoverExpiredTasks(now: Instant): List<AiTaskEvent>
    fun appendTaskEvent(taskId: String, type: String, payloadJson: String, now: Instant): AiTaskEvent
    fun taskEventsAfter(taskId: String, afterId: Long): List<AiTaskEvent>
    fun postingIndex(): List<AiPostingContext>
    fun postingContexts(ids: List<Long>): List<AiPostingContext>
}
