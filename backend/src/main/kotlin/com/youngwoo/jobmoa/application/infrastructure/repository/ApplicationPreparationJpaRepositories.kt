package com.youngwoo.jobmoa.application.infrastructure.repository

import com.youngwoo.jobmoa.application.infrastructure.entity.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ApplicationCaseJpaRepository : JpaRepository<ApplicationCaseEntity, String> {
    fun findByPostingId(postingId: Long): ApplicationCaseEntity?
    fun findAllByOrderByUpdatedAtDescIdDesc(): List<ApplicationCaseEntity>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ApplicationCaseEntity a where a.id = :id")
    fun lockById(@Param("id") id: String): ApplicationCaseEntity?
}

interface ApplicationQuestionJpaRepository : JpaRepository<ApplicationQuestionEntity, String> {
    fun findAllByApplicationIdOrderBySortOrderAscCreatedAtAsc(applicationId: String): List<ApplicationQuestionEntity>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from ApplicationQuestionEntity q where q.id = :id")
    fun lockById(@Param("id") id: String): ApplicationQuestionEntity?
}

interface ApplicationMaterialsDraftJpaRepository : JpaRepository<ApplicationMaterialsDraftEntity, String>

interface ApplicationInterviewSessionJpaRepository : JpaRepository<ApplicationInterviewSessionEntity, String> {
    fun findAllByApplicationIdOrderByCreatedAtDescIdDesc(applicationId: String): List<ApplicationInterviewSessionEntity>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ApplicationInterviewSessionEntity s where s.id = :id")
    fun lockById(@Param("id") id: String): ApplicationInterviewSessionEntity?
}

interface ApplicationInterviewTurnJpaRepository : JpaRepository<ApplicationInterviewTurnEntity, String> {
    fun findAllBySessionIdOrderByPositionAsc(sessionId: String): List<ApplicationInterviewTurnEntity>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ApplicationInterviewTurnEntity t where t.id = :id")
    fun lockById(@Param("id") id: String): ApplicationInterviewTurnEntity?
}

interface ApplicationAiTaskJpaRepository : JpaRepository<ApplicationAiTaskEntity, String> {
    fun findFirstByApplicationIdAndTaskTypeOrderByCreatedAtDescIdDesc(applicationId: String, taskType: String): ApplicationAiTaskEntity?
    fun findAllByApplicationIdOrderByCreatedAtDescIdDesc(applicationId: String): List<ApplicationAiTaskEntity>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ApplicationAiTaskEntity t where t.id = :id")
    fun lockById(@Param("id") id: String): ApplicationAiTaskEntity?
    @Query(value = "SELECT * FROM application_ai_task WHERE status = 'WAITING_AI' OR (status = 'ANALYZING' AND lease_expires_at <= :now) ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    fun nextForUpdate(@Param("now") now: Instant): ApplicationAiTaskEntity?
    @Modifying
    @Query("update ApplicationAiTaskEntity t set t.leaseExpiresAt = :expiresAt, t.updatedAt = :now where t.id = :id and t.status = 'ANALYZING' and t.leaseToken = :token")
    fun renew(@Param("id") id: String, @Param("token") token: String, @Param("now") now: Instant, @Param("expiresAt") expiresAt: Instant): Int
}
