package com.youngwoo.jobmoa.application.infrastructure.repository

import com.youngwoo.jobmoa.application.domain.*
import com.youngwoo.jobmoa.application.infrastructure.entity.*
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Repository
@Transactional
class ApplicationPreparationRepositoryImpl(
    private val casesJpa: ApplicationCaseJpaRepository,
    private val questionsJpa: ApplicationQuestionJpaRepository,
    private val draftsJpa: ApplicationMaterialsDraftJpaRepository,
    private val sessionsJpa: ApplicationInterviewSessionJpaRepository,
    private val turnsJpa: ApplicationInterviewTurnJpaRepository,
    private val tasksJpa: ApplicationAiTaskJpaRepository,
) : ApplicationPreparationRepository, ApplicationAiTaskRepository {
    override fun findCase(id: String) = casesJpa.findById(id).orElse(null)?.domain()
    override fun lockCase(id: String) = casesJpa.lockById(id)?.domain()
    override fun findCaseByPosting(postingId: Long) = casesJpa.findByPostingId(postingId)?.domain()
    override fun cases() = casesJpa.findAllByOrderByUpdatedAtDescIdDesc().map { it.domain() }
    override fun saveCase(value: ApplicationCase) = casesJpa.saveAndFlush(value.entity()).domain()
    override fun questions(applicationId: String) = questionsJpa.findAllByApplicationIdOrderBySortOrderAscCreatedAtAsc(applicationId).map { it.domain() }
    override fun findQuestion(id: String) = questionsJpa.findById(id).orElse(null)?.domain()
    override fun lockQuestion(id: String) = questionsJpa.lockById(id)?.domain()
    override fun saveQuestion(value: ApplicationQuestion) = questionsJpa.saveAndFlush(value.entity()).domain()
    override fun deleteQuestion(id: String) = questionsJpa.deleteById(id)
    override fun draft(applicationId: String) = draftsJpa.findById(applicationId).orElse(null)?.domain()
    override fun saveDraft(value: ApplicationMaterialsDraft) = draftsJpa.saveAndFlush(value.entity()).domain()
    override fun sessions(applicationId: String) = sessionsJpa.findAllByApplicationIdOrderByCreatedAtDescIdDesc(applicationId).map { it.domain() }
    override fun session(id: String) = sessionsJpa.findById(id).orElse(null)?.domain()
    override fun lockSession(id: String) = sessionsJpa.lockById(id)?.domain()
    override fun saveSession(value: ApplicationInterviewSession) = sessionsJpa.saveAndFlush(value.entity()).domain()
    override fun turns(sessionId: String) = turnsJpa.findAllBySessionIdOrderByPositionAsc(sessionId).map { it.domain() }
    override fun findTurn(id: String) = turnsJpa.findById(id).orElse(null)?.domain()
    override fun lockTurn(id: String) = turnsJpa.lockById(id)?.domain()
    override fun saveTurn(value: ApplicationInterviewTurn) = turnsJpa.saveAndFlush(value.entity()).domain()
    override fun find(id: String) = tasksJpa.findById(id).orElse(null)?.domain()
    override fun lock(id: String) = tasksJpa.lockById(id)?.domain()
    override fun latest(applicationId: String, type: ApplicationAiTaskType) = tasksJpa.findFirstByApplicationIdAndTaskTypeOrderByCreatedAtDescIdDesc(applicationId, type.name)?.domain()
    override fun recent(applicationId: String, limit: Int) = tasksJpa.findAllByApplicationIdOrderByCreatedAtDescIdDesc(applicationId).take(limit).map { it.domain() }
    override fun save(task: ApplicationAiTask) = tasksJpa.saveAndFlush(task.entity()).domain()
    override fun claimNext(now: Instant, lease: Duration): ApplicationAiTask? {
        val row = tasksJpa.nextForUpdate(now) ?: return null
        row.status = ApplicationAiTaskStatus.ANALYZING.name
        row.attempts += 1
        row.leaseToken = UUID.randomUUID().toString()
        row.leaseExpiresAt = now.plus(lease)
        row.updatedAt = now
        return tasksJpa.saveAndFlush(row).domain()
    }
    override fun renew(id: String, token: String, now: Instant, lease: Duration) = tasksJpa.renew(id, token, now, now.plus(lease)) == 1
}
