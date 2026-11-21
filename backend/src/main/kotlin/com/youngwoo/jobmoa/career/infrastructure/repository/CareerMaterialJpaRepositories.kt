package com.youngwoo.jobmoa.career.infrastructure.repository

import com.youngwoo.jobmoa.career.infrastructure.entity.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface CareerMaterialJpaRepository : JpaRepository<CareerMaterialEntity, String> {
    fun findByContentHash(contentHash: String): CareerMaterialEntity?
    fun findAllByOrderByCreatedAtDescIdDesc(): List<CareerMaterialEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CareerMaterialEntity m where m.id = :id")
    fun lockById(@Param("id") id: String): CareerMaterialEntity?

    @Query(value = """
        SELECT * FROM career_material
        WHERE status = 'WAITING_AI' OR (status = 'ANALYZING' AND lease_expires_at <= :now)
        ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    fun nextForUpdate(@Param("now") now: Instant): CareerMaterialEntity?

    @Modifying
    @Query("update CareerMaterialEntity m set m.leaseExpiresAt = :expiresAt, m.updatedAt = :now where m.id = :id and m.status = 'ANALYZING' and m.leaseToken = :token")
    fun renew(@Param("id") id: String, @Param("token") token: String, @Param("now") now: Instant, @Param("expiresAt") expiresAt: Instant): Int
}

interface CareerMaterialFragmentJpaRepository : JpaRepository<CareerMaterialFragmentEntity, String> {
    fun findAllByMaterialIdOrderByOrdinalAsc(materialId: String): List<CareerMaterialFragmentEntity>
    fun findAllByMaterialIdIn(materialIds: List<String>): List<CareerMaterialFragmentEntity>
}

interface CareerFactJpaRepository : JpaRepository<CareerFactEntity, String> {
    fun findAllByMaterialIdOrderByCreatedAtAscIdAsc(materialId: String): List<CareerFactEntity>
    fun findAllByMaterialIdIn(materialIds: List<String>): List<CareerFactEntity>
    fun findAllByVerificationStatusOrderByCreatedAtAscIdAsc(verificationStatus: String): List<CareerFactEntity>

    @Modifying
    @Query("delete from CareerFactEntity f where f.materialId = :materialId")
    fun deleteAllForMaterial(@Param("materialId") materialId: String)
}

interface CareerFactEvidenceJpaRepository : JpaRepository<CareerFactEvidenceEntity, String> {
    fun findAllByFactIdOrderByIdAsc(factId: String): List<CareerFactEvidenceEntity>
    fun findAllByFactIdInOrderByFactIdAscIdAsc(factIds: List<String>): List<CareerFactEvidenceEntity>
}
