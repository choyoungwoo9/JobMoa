package com.youngwoo.jobmoa.collection.infrastructure.repository

import com.youngwoo.jobmoa.collection.domain.model.*
import com.youngwoo.jobmoa.collection.domain.repository.CrawlTaskRepository
import com.youngwoo.jobmoa.collection.infrastructure.entity.entity
import com.youngwoo.jobmoa.shared.ResourceNotFound
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Repository
@Transactional
class CrawlTaskRepositoryImpl(private val tasks: CrawlTaskJpaRepository) : CrawlTaskRepository {
    override fun enqueue(task: CrawlTask): Boolean = tasks.enqueue(task) > 0
    override fun pendingSites(): List<String> = tasks.pendingSites()
    override fun claim(siteKey: String, duration: Duration): CrawlTask? {
        val entity = tasks.nextForUpdate(siteKey) ?: return null
        entity.status = TaskStatus.PROCESSING.name
        entity.attempts += 1
        entity.leaseToken = UUID.randomUUID().toString()
        entity.leaseExpiresAt = Instant.now().plus(duration)
        return entity.domain()
    }
    override fun find(id: String): CrawlTask? = tasks.findById(id).orElse(null)?.domain()
    override fun lock(id: String): CrawlTask = tasks.lockById(id)?.domain() ?: throw ResourceNotFound("작업을 찾을 수 없습니다.")
    override fun save(task: CrawlTask) { tasks.save(task.entity()) }
    override fun renew(id: String, token: String, duration: Duration): Boolean {
        val now = Instant.now()
        return tasks.renew(id, token, now, now.plus(duration)) == 1
    }
    override fun recoverExpired(now: Instant): Int = tasks.recoverExpired(now)
    override fun tasks(runId: String): List<CrawlTask> = tasks.findAllByRunIdOrderByDiscoveredAtAscIdAsc(runId).map { it.domain() }
}
