package com.youngwoo.jobmoa.career.assistant.infrastructure.repository

import com.youngwoo.jobmoa.career.assistant.infrastructure.entity.CareerAssistantTaskEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface CareerAssistantTaskJpaRepository : JpaRepository<CareerAssistantTaskEntity, String> {
    fun findAllByTaskTypeOrderByCreatedAtDescIdDesc(taskType: String): List<CareerAssistantTaskEntity>
    fun findAllByParentTaskIdOrderByCreatedAtDescIdDesc(parentTaskId: String): List<CareerAssistantTaskEntity>
    fun findFirstByParentTaskIdAndPostingIdOrderByCreatedAtDescIdDesc(parentTaskId: String, postingId: Long): CareerAssistantTaskEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CareerAssistantTaskEntity t where t.id = :id")
    fun lockById(@Param("id") id: String): CareerAssistantTaskEntity?

    @Query(value = """
        SELECT * FROM career_assistant_task
        WHERE status = 'WAITING_AI' OR (status = 'ANALYZING' AND lease_expires_at <= :now)
        ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    fun nextForUpdate(@Param("now") now: Instant): CareerAssistantTaskEntity?

    @Modifying
    @Query("update CareerAssistantTaskEntity t set t.leaseExpiresAt = :expiresAt, t.updatedAt = :now where t.id = :id and t.status = 'ANALYZING' and t.leaseToken = :token")
    fun renew(@Param("id") id: String, @Param("token") token: String, @Param("now") now: Instant, @Param("expiresAt") expiresAt: Instant): Int
}
