package com.youngwoo.jobmoa.collection.infrastructure.repository

import com.youngwoo.jobmoa.collection.domain.model.*
import com.youngwoo.jobmoa.collection.domain.repository.CollectionRepository
import com.youngwoo.jobmoa.collection.infrastructure.entity.*
import com.youngwoo.jobmoa.shared.CollectionConflict
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
@Transactional
class CollectionRepositoryImpl(
    private val runs: CollectionRunJpaRepository,
    private val locks: CollectionLockJpaRepository,
    private val sources: RunSourceJpaRepository,
    private val tasks: CrawlTaskJpaRepository,
) : CollectionRepository {
    private fun globalLock() = locks.lockById("collection") ?: error("수집 잠금 초기화가 필요합니다.")
    override fun activeRunForUpdate(): CollectionRun? = globalLock().activeRunId?.let(::findRun)?.takeIf { it.active }
    override fun activate(run: CollectionRun, sources: List<RunSource>) {
        val lock = globalLock()
        if (lock.activeRunId?.let(::findRun)?.active == true) throw CollectionConflict("이미 수집이 실행 중입니다.")
        runs.save(run.entity())
        this.sources.saveAll(sources.map { it.entity() })
        lock.activeRunId = run.id
    }
    override fun findRun(id: String): CollectionRun? = runs.findById(id).orElse(null)?.domain()
    override fun recentRuns(limit: Int): List<CollectionRun> = runs.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit.coerceIn(1, 100))).map { it.domain() }
    override fun sources(runId: String): List<RunSource> = sources.findAllByRunIdOrderBySourceId(runId).map { it.domain() }
    override fun nextDiscovery(): RunSource? {
        val source = sources.nextForUpdate() ?: return null
        source.status = DiscoveryStatus.RUNNING.name
        return source.domain()
    }
    override fun recoverDiscovery(): Int = sources.recoverInterrupted()
    override fun saveSource(source: RunSource) { sources.save(source.entity()) }
    override fun finishIfReady(runId: String, now: Instant) {
        val run = runs.lockById(runId) ?: return
        if (run.finishedAt != null) return
        val sourceResults = sources(runId)
        if (sourceResults.any { it.status == DiscoveryStatus.PENDING || it.status == DiscoveryStatus.RUNNING }) return
        run.status = RunStatus.RUNNING.name
        val counts = tasks.statusCounts(runId).associate { it.status to it.total }
        if ((counts[TaskStatus.PENDING.name] ?: 0) + (counts[TaskStatus.PROCESSING.name] ?: 0) > 0) return
        val failures = sourceResults.count { it.status == DiscoveryStatus.FAILED } + (counts[TaskStatus.FAILED.name] ?: 0)
        run.status = when {
            failures == 0L -> RunStatus.COMPLETED.name
            (counts[TaskStatus.COMPLETED.name] ?: 0) > 0 || sourceResults.any { it.status == DiscoveryStatus.COMPLETED } -> RunStatus.PARTIAL_FAILED.name
            else -> RunStatus.FAILED.name
        }
        run.finishedAt = now
        val lock = globalLock()
        if (lock.activeRunId == runId) lock.activeRunId = null
    }
}
