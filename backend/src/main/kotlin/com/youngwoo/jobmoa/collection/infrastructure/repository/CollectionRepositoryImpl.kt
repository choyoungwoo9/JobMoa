package com.youngwoo.jobmoa.collection.infrastructure.repository

import com.youngwoo.jobmoa.collection.domain.model.*
import com.youngwoo.jobmoa.collection.domain.repository.CollectionRepository
import com.youngwoo.jobmoa.collection.infrastructure.entity.*
import com.youngwoo.jobmoa.shared.CollectionConflict
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository @Transactional
class CollectionRepositoryImpl(private val em: EntityManager) : CollectionRepository {
    override fun activeRunForUpdate(): CollectionRun? {
        val lock = em.find(CollectionLockEntity::class.java, "collection", LockModeType.PESSIMISTIC_WRITE)
        return lock.activeRunId?.let(::findRun)?.takeIf { it.active }
    }

    override fun activate(run: CollectionRun, sources: List<RunSource>) {
        if (activeRunForUpdate() != null) throw CollectionConflict("이미 수집이 실행 중입니다.")
        em.persist(run.entity())
        sources.forEach { em.persist(it.entity()) }
        em.find(CollectionLockEntity::class.java, "collection").activeRunId = run.id
    }

    @Transactional(readOnly = true)
    override fun findRun(id: String): CollectionRun? = em.find(CollectionRunEntity::class.java, id)?.domain()

    @Transactional(readOnly = true)
    override fun recentRuns(limit: Int): List<CollectionRun> = em.createQuery(
        "select r from CollectionRunEntity r order by r.startedAt desc", CollectionRunEntity::class.java
    ).setMaxResults(limit.coerceIn(1, 100)).resultList.map { it.domain() }

    @Transactional(readOnly = true)
    override fun sources(runId: String): List<RunSource> = em.createQuery(
        "select s from RunSourceEntity s where s.runId = :runId order by s.sourceId", RunSourceEntity::class.java
    ).setParameter("runId", runId).resultList.map { it.domain() }

    override fun nextDiscovery(): RunSource? {
        val id = em.createNativeQuery("""
            SELECT s.id FROM collection_run_source s JOIN collection_run r ON r.id = s.run_id
            WHERE s.status = 'PENDING' AND r.finished_at IS NULL
            ORDER BY r.started_at, s.source_id LIMIT 1 FOR UPDATE SKIP LOCKED
        """).resultList.firstOrNull() as? String ?: return null
        val source = em.find(RunSourceEntity::class.java, id)
        source.status = DiscoveryStatus.RUNNING.name
        return source.domain()
    }

    override fun saveSource(source: RunSource) { em.merge(source.entity()) }

    override fun finishIfReady(runId: String, now: Instant) {
        val run = em.find(CollectionRunEntity::class.java, runId, LockModeType.PESSIMISTIC_WRITE) ?: return
        if (run.finishedAt != null) return
        val sources = sources(runId)
        if (sources.any { it.status == DiscoveryStatus.PENDING || it.status == DiscoveryStatus.RUNNING }) return
        run.status = RunStatus.RUNNING.name
        val counts = em.createQuery("select t.status, count(t) from CrawlTaskEntity t where t.runId = :id group by t.status", Array<Any>::class.java)
            .setParameter("id", runId).resultList.associate { it[0].toString() to (it[1] as Number).toLong() }
        if ((counts[TaskStatus.PENDING.name] ?: 0) + (counts[TaskStatus.PROCESSING.name] ?: 0) > 0) return
        val failures = sources.count { it.status == DiscoveryStatus.FAILED } + (counts[TaskStatus.FAILED.name] ?: 0)
        val successes = counts[TaskStatus.COMPLETED.name] ?: 0
        run.status = when {
            failures == 0L -> RunStatus.COMPLETED.name
            successes > 0 || sources.any { it.status == DiscoveryStatus.COMPLETED } -> RunStatus.PARTIAL_FAILED.name
            else -> RunStatus.FAILED.name
        }
        run.finishedAt = now
        val lock = em.find(CollectionLockEntity::class.java, "collection", LockModeType.PESSIMISTIC_WRITE)
        if (lock.activeRunId == runId) lock.activeRunId = null
    }
}
