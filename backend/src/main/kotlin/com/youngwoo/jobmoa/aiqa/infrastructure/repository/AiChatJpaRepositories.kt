package com.youngwoo.jobmoa.aiqa.infrastructure.repository

import com.youngwoo.jobmoa.aiqa.infrastructure.entity.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface AiConversationPreviewProjection {
    val id: String
    val title: String
    val updatedAt: Instant
    val preview: String
}

interface AiConversationJpaRepository : JpaRepository<AiConversationEntity, String> {
    @Query(value = """
        SELECT c.id AS id, c.title AS title, c.updated_at AS updatedAt,
            COALESCE((SELECT LEFT(m.content, 240) FROM ai_message m
                WHERE m.conversation_id = c.id ORDER BY m.created_at DESC, m.id DESC LIMIT 1), '') AS preview
        FROM ai_conversation c ORDER BY c.updated_at DESC, c.id DESC
    """, nativeQuery = true)
    fun previews(): List<AiConversationPreviewProjection>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AiConversationEntity c where c.id = :id")
    fun lockById(@Param("id") id: String): AiConversationEntity?
}

interface AiMessageJpaRepository : JpaRepository<AiMessageEntity, String> {
    fun findAllByConversationIdOrderByCreatedAtAscIdAsc(conversationId: String): List<AiMessageEntity>
    fun findTopByConversationIdOrderByCreatedAtDescIdDesc(conversationId: String): AiMessageEntity?
    fun findAllByIdIn(ids: List<String>): List<AiMessageEntity>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from AiMessageEntity m where m.id = :id")
    fun lockById(@Param("id") id: String): AiMessageEntity?
}

interface AiTaskJpaRepository : JpaRepository<AiTaskEntity, String> {
    @Query(value = "SELECT * FROM ai_task WHERE status = 'WAITING' ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    fun nextForUpdate(): AiTaskEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AiTaskEntity t where t.id = :id")
    fun lockById(@Param("id") id: String): AiTaskEntity?

    fun findFirstByAssistantMessageId(assistantMessageId: String): AiTaskEntity?

    fun findAllByStatusAndLeaseExpiresAtBefore(status: String, before: Instant): List<AiTaskEntity>

    @org.springframework.data.jpa.repository.Modifying
    @Query("""update AiTaskEntity t set t.leaseExpiresAt = :expires, t.updatedAt = :now
        where t.id = :id and t.status = 'PROCESSING' and t.leaseToken = :token and t.leaseExpiresAt > :now""")
    fun renew(@Param("id") id: String, @Param("token") token: String, @Param("now") now: Instant, @Param("expires") expires: Instant): Int

}

interface AiCitationJpaRepository : JpaRepository<AiCitationEntity, Long> {
    fun findAllByMessageIdOrderByPositionAsc(messageId: String): List<AiCitationEntity>
    @org.springframework.data.jpa.repository.Modifying
    fun deleteAllByMessageId(messageId: String)
}

interface AiTaskEventJpaRepository : JpaRepository<AiTaskEventEntity, Long> {
    fun findAllByTaskIdAndIdGreaterThanOrderByIdAsc(taskId: String, afterId: Long): List<AiTaskEventEntity>
}

interface AiPostingContextProjection {
    val id: Long
    val sourceId: String
    val company: String
    val title: String
    val location: String
    val url: String
    val firstSeen: Instant
    val closed: Boolean
    val readFlag: Boolean
    val categoryIds: String
    val versionId: Long?
    val versionSavedAt: Instant?
    val body: String?
}

interface AiPostingContextJpaRepository : JpaRepository<com.youngwoo.jobmoa.posting.infrastructure.entity.PostingEntity, Long> {
    @Query(value = """
        SELECT p.id AS id, p.source_id AS sourceId, p.company AS company, p.title AS title,
            p.location AS location, p.url AS url, p.first_seen AS firstSeen, p.closed AS closed,
            p.is_read AS readFlag,
            COALESCE((SELECT GROUP_CONCAT(pc.category_id ORDER BY pc.category_id SEPARATOR ',')
                FROM posting_category pc WHERE pc.posting_id = p.id), '') AS categoryIds,
            v.id AS versionId, v.saved_at AS versionSavedAt, v.body_text AS body
        FROM posting p
        LEFT JOIN posting_version v ON v.id = (
            SELECT v2.id FROM posting_version v2 WHERE v2.posting_id = p.id
            ORDER BY v2.saved_at DESC, v2.id DESC LIMIT 1
        )
        ORDER BY p.first_seen DESC, p.id DESC
    """, nativeQuery = true)
    fun allContexts(): List<AiPostingContextProjection>

    @Query(value = """
        SELECT p.id AS id, p.source_id AS sourceId, p.company AS company, p.title AS title,
            p.location AS location, p.url AS url, p.first_seen AS firstSeen, p.closed AS closed,
            p.is_read AS readFlag,
            COALESCE((SELECT GROUP_CONCAT(pc.category_id ORDER BY pc.category_id SEPARATOR ',')
                FROM posting_category pc WHERE pc.posting_id = p.id), '') AS categoryIds,
            v.id AS versionId, v.saved_at AS versionSavedAt, v.body_text AS body
        FROM posting p
        LEFT JOIN posting_version v ON v.id = (
            SELECT v2.id FROM posting_version v2 WHERE v2.posting_id = p.id
            ORDER BY v2.saved_at DESC, v2.id DESC LIMIT 1
        )
        WHERE p.id IN (:ids)
    """, nativeQuery = true)
    fun contextsByIds(@Param("ids") ids: List<Long>): List<AiPostingContextProjection>
}
