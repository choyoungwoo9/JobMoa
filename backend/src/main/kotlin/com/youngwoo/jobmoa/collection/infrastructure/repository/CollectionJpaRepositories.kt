package com.youngwoo.jobmoa.collection.infrastructure.repository

import com.youngwoo.jobmoa.collection.domain.model.CrawlTask
import com.youngwoo.jobmoa.collection.infrastructure.entity.*
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param
import java.time.Instant

interface CollectionRunJpaRepository : JpaRepository<CollectionRunEntity, String> {
    fun findAllByOrderByStartedAtDesc(pageable: Pageable): List<CollectionRunEntity>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CollectionRunEntity r where r.id = :id")
    fun lockById(@Param("id") id: String): CollectionRunEntity?
}

interface CollectionLockJpaRepository : JpaRepository<CollectionLockEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CollectionLockEntity l where l.id = :id")
    fun lockById(@Param("id") id: String): CollectionLockEntity?
}

interface RunSourceJpaRepository : JpaRepository<RunSourceEntity, String> {
    fun findAllByRunIdOrderBySourceId(runId: String): List<RunSourceEntity>
    @Query(value = """
        SELECT s.* FROM collection_run_source s JOIN collection_run r ON r.id = s.run_id
        WHERE s.status = 'PENDING' AND r.finished_at IS NULL
        ORDER BY r.started_at, s.source_id LIMIT 1 FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    fun nextForUpdate(): RunSourceEntity?
    @Modifying
    @Query("update RunSourceEntity s set s.status = 'PENDING' where s.status = 'RUNNING'")
    fun recoverInterrupted(): Int
}

interface TaskStatusCount { val status: String; val total: Long }
interface TaskSummaryProjection {
    val id: String; val sourceId: String; val company: String; val title: String
    val url: String; val urlHash: String; val status: String; val stage: String
    val attempts: Int; val failedAt: Instant?; val error: String?; val outcome: String?
    val resolvedAt: Instant?; val retryOfTaskId: String?
    val categoryId: String; val companyId: String; val reusedVersionId: Long?
}

interface CrawlTaskJpaRepository : JpaRepository<CrawlTaskEntity, String> {
    @Query("""select t.id as id, t.sourceId as sourceId, t.company as company, t.title as title,
        t.url as url, t.urlHash as urlHash, t.status as status, t.stage as stage, t.attempts as attempts,
        t.failedAt as failedAt, t.error as error, t.outcome as outcome, t.resolvedAt as resolvedAt,
        t.retryOfTaskId as retryOfTaskId, t.categoryId as categoryId, t.companyId as companyId, t.reusedVersionId as reusedVersionId from CrawlTaskEntity t where t.runId = :id order by t.discoveredAt, t.id""")
    fun summaries(@Param("id") runId: String): List<TaskSummaryProjection>
    fun findAllByRunIdOrderByDiscoveredAtAscIdAsc(runId: String): List<CrawlTaskEntity>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CrawlTaskEntity t where t.id = :id")
    fun lockById(@Param("id") id: String): CrawlTaskEntity?
    @Query("select t.status as status, count(t) as total from CrawlTaskEntity t where t.runId = :id group by t.status")
    fun statusCounts(@Param("id") runId: String): List<TaskStatusCount>
    @Query(value = """
        SELECT DISTINCT t.site_key FROM crawl_task t JOIN collection_run r ON r.id = t.run_id
        WHERE t.status = 'PENDING' AND r.finished_at IS NULL ORDER BY t.site_key LIMIT 200
    """, nativeQuery = true)
    fun pendingSites(): List<String>
    @Query(value = """
        SELECT t.* FROM crawl_task t JOIN collection_run r ON r.id = t.run_id
        WHERE t.status = 'PENDING' AND t.site_key = :site AND r.finished_at IS NULL
        ORDER BY t.discovered_at, t.id LIMIT 1 FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    fun nextForUpdate(@Param("site") siteKey: String): CrawlTaskEntity?
    @Modifying
    @Query("""update CrawlTaskEntity t set t.leaseExpiresAt = :expires
        where t.id = :id and t.status = 'PROCESSING' and t.leaseToken = :token and t.leaseExpiresAt > :now""")
    fun renew(@Param("id") id: String, @Param("token") token: String, @Param("now") now: Instant, @Param("expires") expires: Instant): Int
    @Modifying
    @Query("""update CrawlTaskEntity t set t.status = 'PENDING', t.leaseToken = null, t.leaseExpiresAt = null
        where t.status = 'PROCESSING' and t.leaseExpiresAt < :now""")
    fun recoverExpired(@Param("now") now: Instant): Int
    @Modifying
    @Query(value = """
        INSERT INTO crawl_task
        (id,run_id,source_id,company,url,url_hash,site_key,discovered_at,title,location,status,stage,attempts,body_text,content_hash,capture_path,retry_of_task_id,closed,category_id,company_id,reused_version_id)
        VALUES (:#{#task.id},:#{#task.runId},:#{#task.sourceId},:#{#task.company},:#{#task.url},:#{#task.urlHash},:#{#task.siteKey},:#{#task.discoveredAt},:#{#task.title},:#{#task.location},:#{#task.status.name()},:#{#task.stage.name()},:#{#task.attempts},:#{#task.bodyText},:#{#task.contentHash},:#{#task.capturePath},:#{#task.retryOfTaskId},:#{#task.closed},:#{#task.categoryId},:#{#task.companyId},:#{#task.reusedVersionId})
        ON DUPLICATE KEY UPDATE id = id
    """, nativeQuery = true)
    fun enqueue(@Param("task") task: CrawlTask): Int
}
