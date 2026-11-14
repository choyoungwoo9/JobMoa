package com.youngwoo.jobmoa.collection.infrastructure.repository

import com.youngwoo.jobmoa.collection.domain.model.*
import com.youngwoo.jobmoa.collection.domain.repository.CrawlTaskRepository
import com.youngwoo.jobmoa.collection.infrastructure.entity.*
import com.youngwoo.jobmoa.shared.ResourceNotFound
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Repository @Transactional
class CrawlTaskRepositoryImpl(private val em: EntityManager) : CrawlTaskRepository {
    override fun enqueue(task: CrawlTask): Boolean {
        val values = linkedMapOf<String, Any?>(
            "id" to task.id, "run_id" to task.runId, "source_id" to task.sourceId,
            "company" to task.company, "url" to task.url, "url_hash" to task.urlHash,
            "site_key" to task.siteKey, "discovered_at" to task.discoveredAt,
            "title" to task.title, "location" to task.location, "status" to task.status.name,
            "stage" to task.stage.name, "attempts" to task.attempts,
            "body_text" to task.bodyText, "content_hash" to task.contentHash,
            "capture_path" to task.capturePath, "retry_of_task_id" to task.retryOfTaskId,
            "closed" to task.closed,
        )
        val query = em.createNativeQuery("INSERT IGNORE INTO crawl_task (${values.keys.joinToString()}) VALUES (${values.keys.joinToString { ":$it" }})")
        values.forEach { (key, value) -> query.setParameter(key, value) }
        return query.executeUpdate() == 1
    }

    @Transactional(readOnly = true)
    override fun pendingSites(): List<String> = em.createNativeQuery("""
        SELECT DISTINCT t.site_key FROM crawl_task t JOIN collection_run r ON r.id = t.run_id
        WHERE t.status = 'PENDING' AND r.finished_at IS NULL ORDER BY t.site_key LIMIT 200
    """).resultList.map { it.toString() }

    override fun claim(siteKey: String, duration: Duration): CrawlTask? {
        val id = em.createNativeQuery("""
            SELECT t.id FROM crawl_task t JOIN collection_run r ON r.id = t.run_id
            WHERE t.status = 'PENDING' AND t.site_key = :site AND r.finished_at IS NULL
            ORDER BY t.discovered_at, t.id LIMIT 1 FOR UPDATE SKIP LOCKED
        """).setParameter("site", siteKey).resultList.firstOrNull() as? String ?: return null
        val entity = em.find(CrawlTaskEntity::class.java, id)
        entity.status = TaskStatus.PROCESSING.name
        entity.attempts += 1
        entity.leaseToken = UUID.randomUUID().toString()
        entity.leaseExpiresAt = Instant.now().plus(duration)
        return entity.domain()
    }

    @Transactional(readOnly = true)
    override fun find(id: String): CrawlTask? = em.find(CrawlTaskEntity::class.java, id)?.domain()
    override fun lock(id: String): CrawlTask = em.find(CrawlTaskEntity::class.java, id, LockModeType.PESSIMISTIC_WRITE)?.domain()
        ?: throw ResourceNotFound("작업을 찾을 수 없습니다.")
    override fun save(task: CrawlTask) { em.merge(task.entity()) }

    override fun renew(id: String, token: String, duration: Duration): Boolean = em.createNativeQuery("""
        UPDATE crawl_task SET lease_expires_at = :expires
        WHERE id = :id AND status = 'PROCESSING' AND lease_token = :token AND lease_expires_at > UTC_TIMESTAMP(6)
    """).setParameter("expires", Instant.now().plus(duration)).setParameter("id", id).setParameter("token", token).executeUpdate() == 1

    override fun recoverExpired(now: Instant): Int = em.createNativeQuery("""
        UPDATE crawl_task SET status = 'PENDING', lease_token = NULL, lease_expires_at = NULL
        WHERE status = 'PROCESSING' AND lease_expires_at < :now
    """).setParameter("now", now).executeUpdate()

    @Transactional(readOnly = true)
    override fun tasks(runId: String): List<CrawlTask> = em.createQuery(
        "select t from CrawlTaskEntity t where t.runId = :runId order by t.discoveredAt, t.id", CrawlTaskEntity::class.java
    ).setParameter("runId", runId).resultList.map { it.domain() }
}
