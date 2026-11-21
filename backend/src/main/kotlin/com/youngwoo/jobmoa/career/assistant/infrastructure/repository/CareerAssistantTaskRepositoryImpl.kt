package com.youngwoo.jobmoa.career.assistant.infrastructure.repository

import com.youngwoo.jobmoa.career.assistant.domain.model.CareerAssistantTask
import com.youngwoo.jobmoa.career.assistant.domain.model.CareerAssistantTaskStatus
import com.youngwoo.jobmoa.career.assistant.domain.repository.CareerAssistantTaskRepository
import com.youngwoo.jobmoa.career.assistant.infrastructure.entity.*
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Repository
@Transactional
class CareerAssistantTaskRepositoryImpl(private val tasks: CareerAssistantTaskJpaRepository) : CareerAssistantTaskRepository {
    override fun find(id: String) = tasks.findById(id).orElse(null)?.domain()
    override fun lock(id: String) = tasks.lockById(id)?.domain()

    override fun save(task: CareerAssistantTask) = tasks.saveAndFlush(task.entity()).domain()

    override fun recent(type: String, limit: Int) = tasks.findAllByTaskTypeOrderByCreatedAtDescIdDesc(type).take(limit).map { it.domain() }

    override fun drafts(parentTaskId: String) = tasks.findAllByParentTaskIdOrderByCreatedAtDescIdDesc(parentTaskId)
        .filter { it.taskType == "POSTING_DRAFT" }.map { it.domain() }

    override fun draft(parentTaskId: String, postingId: Long) =
        tasks.findFirstByParentTaskIdAndPostingIdOrderByCreatedAtDescIdDesc(parentTaskId, postingId)
            ?.takeIf { it.taskType == "POSTING_DRAFT" }?.domain()

    override fun claimNext(now: Instant, lease: Duration): CareerAssistantTask? {
        val row = tasks.nextForUpdate(now) ?: return null
        row.status = CareerAssistantTaskStatus.ANALYZING.name
        row.attempts += 1
        row.leaseToken = UUID.randomUUID().toString()
        row.leaseExpiresAt = now.plus(lease)
        row.updatedAt = now
        return tasks.saveAndFlush(row).domain()
    }

    override fun renew(id: String, token: String, now: Instant, lease: Duration) =
        tasks.renew(id, token, now, now.plus(lease)) == 1
}
