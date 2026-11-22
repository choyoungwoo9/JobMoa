package com.youngwoo.jobmoa.applicationrequirements.infrastructure

import com.youngwoo.jobmoa.applicationrequirements.domain.*
import com.youngwoo.jobmoa.posting.infrastructure.entity.PostingEntity
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "posting_application_requirement")
open class ApplicationRequirementEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "posting_id", nullable = false) open var postingId: Long = 0,
    @field:Column(nullable = false, length = 24) open var source: String = RequirementSource.USER_PASTED.name,
    @field:Column(name = "source_url", length = 2048) open var sourceUrl: String? = null,
    @field:Column(name = "raw_text", nullable = false, columnDefinition = "longtext") open var rawText: String = "",
    @field:Column(name = "result_json", columnDefinition = "longtext") open var resultJson: String? = null,
    @field:Column(nullable = false, length = 24) open var status: String = RequirementStatus.WAITING_AI.name,
    @field:Column(nullable = false) open var attempts: Int = 0,
    @field:Column(name = "error_message", columnDefinition = "longtext") open var errorMessage: String? = null,
    @field:Column(name = "lease_token", length = 36) open var leaseToken: String? = null,
    @field:Column(name = "lease_expires_at") open var leaseExpiresAt: Instant? = null,
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.now(),
    @field:Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.now(),
    @field:Column(name = "confirmed_at") open var confirmedAt: Instant? = null,
    @field:Column(name = "collection_run_id", length = 36) open var collectionRunId: String? = null,
    @field:Column(name = "public_page_fetched", nullable = false) open var publicPageFetched: Boolean = false,
) {
    fun domain() = ApplicationRequirement(id, postingId, RequirementSource.valueOf(source), sourceUrl, rawText,
        resultJson, RequirementStatus.valueOf(status), attempts, errorMessage, leaseToken, leaseExpiresAt,
        createdAt, updatedAt, confirmedAt, collectionRunId, publicPageFetched)
}

private fun ApplicationRequirement.entity() = ApplicationRequirementEntity(id, postingId, source.name, sourceUrl, rawText,
    resultJson, status.name, attempts, errorMessage, leaseToken, leaseExpiresAt, createdAt, updatedAt, confirmedAt,
    collectionRunId, publicPageFetched)

interface ApplicationRequirementJpaRepository : JpaRepository<ApplicationRequirementEntity, String> {
    fun findAllByPostingIdOrderByCreatedAtDescIdDesc(postingId: Long): List<ApplicationRequirementEntity>
    fun findFirstByCollectionRunIdAndPostingId(collectionRunId: String, postingId: Long): ApplicationRequirementEntity?
    @Query("select r.status as status, r.publicPageFetched as publicPageFetched, count(r) as total from ApplicationRequirementEntity r where r.collectionRunId = :runId group by r.status, r.publicPageFetched")
    fun collectionStatusCounts(@Param("runId") runId: String): List<ApplicationRequirementStatusCountProjection>
    @Query("select r.postingId as postingId, p.sourceId as sourceId, p.company as company, p.url as url, r.status as status, r.publicPageFetched as publicPageFetched, r.errorMessage as errorMessage from ApplicationRequirementEntity r, PostingEntity p where p.id = r.postingId and r.collectionRunId = :runId")
    fun collectionRunSummaries(@Param("runId") runId: String): List<ApplicationRequirementRunSummaryProjection>
    @Query("select r.postingId as postingId, p.sourceId as sourceId, p.company as company, p.url as url, r.status as status, r.publicPageFetched as publicPageFetched, r.errorMessage as errorMessage from ApplicationRequirementEntity r, PostingEntity p where p.id = r.postingId and r.source = 'PUBLIC_PAGE' and not exists (select newer.id from ApplicationRequirementEntity newer where newer.postingId = r.postingId and newer.source = 'PUBLIC_PAGE' and (newer.createdAt > r.createdAt or (newer.createdAt = r.createdAt and newer.id > r.id)))")
    fun latestPublicSummaries(): List<ApplicationRequirementRunSummaryProjection>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ApplicationRequirementEntity r where r.id = :id")
    fun lockById(@Param("id") id: String): ApplicationRequirementEntity?

    @Query(value = "SELECT * FROM posting_application_requirement WHERE status='WAITING_AI' OR (status='ANALYZING' AND lease_expires_at <= :now) ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    fun nextForUpdate(@Param("now") now: Instant): ApplicationRequirementEntity?

    @Query(value = "SELECT * FROM posting_application_requirement WHERE status='WAITING_PUBLIC' OR (status='FETCHING_PUBLIC' AND lease_expires_at <= :now) ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    fun nextPublicPageForUpdate(@Param("now") now: Instant): ApplicationRequirementEntity?

    @Modifying
    @Query("update ApplicationRequirementEntity r set r.leaseExpiresAt = :expiresAt, r.updatedAt = :now where r.id = :id and r.status = 'ANALYZING' and r.leaseToken = :token")
    fun renew(@Param("id") id: String, @Param("token") token: String, @Param("now") now: Instant, @Param("expiresAt") expiresAt: Instant): Int

    @Modifying
    @Query("update ApplicationRequirementEntity r set r.leaseExpiresAt = :expiresAt, r.updatedAt = :now where r.id = :id and r.status = 'FETCHING_PUBLIC' and r.leaseToken = :token")
    fun renewPublicPage(@Param("id") id: String, @Param("token") token: String, @Param("now") now: Instant, @Param("expiresAt") expiresAt: Instant): Int
}

interface ApplicationRequirementStatusCountProjection {
    val status: String
    val publicPageFetched: Boolean
    val total: Long
}

interface ApplicationRequirementRunSummaryProjection {
    val postingId: Long
    val sourceId: String
    val company: String
    val url: String
    val status: String
    val publicPageFetched: Boolean
    val errorMessage: String?
}

@Repository
@Transactional
class ApplicationRequirementPersistence(private val jpa: ApplicationRequirementJpaRepository) : ApplicationRequirementRepository {
    override fun find(id: String) = jpa.findById(id).orElse(null)?.domain()
    override fun findByPosting(postingId: Long) = jpa.findAllByPostingIdOrderByCreatedAtDescIdDesc(postingId).map { it.domain() }
    override fun findByCollectionRunAndPosting(runId: String, postingId: Long) = jpa.findFirstByCollectionRunIdAndPostingId(runId, postingId)?.domain()
    override fun countByCollectionRun(runId: String) = jpa.collectionStatusCounts(runId).map {
        ApplicationRequirementStatusCount(RequirementStatus.valueOf(it.status), it.publicPageFetched, it.total.toInt())
    }
    override fun summarizeCollectionRun(runId: String) = jpa.collectionRunSummaries(runId).map {
        ApplicationRequirementRunSummary(it.postingId, it.sourceId, it.company, it.url, RequirementStatus.valueOf(it.status), it.publicPageFetched, it.errorMessage)
    }
    override fun latestPublicSummary() = jpa.latestPublicSummaries().map {
        ApplicationRequirementRunSummary(it.postingId, it.sourceId, it.company, it.url, RequirementStatus.valueOf(it.status), it.publicPageFetched, it.errorMessage)
    }
    override fun lock(id: String) = jpa.lockById(id)?.domain()
    override fun save(requirement: ApplicationRequirement) { jpa.save(requirement.entity()) }
    override fun claim(now: Instant, lease: Duration): ApplicationRequirement? {
        val row = jpa.nextForUpdate(now) ?: return null
        row.status = RequirementStatus.ANALYZING.name
        row.attempts++
        row.leaseToken = UUID.randomUUID().toString()
        row.leaseExpiresAt = now.plus(lease)
        row.updatedAt = now
        return jpa.saveAndFlush(row).domain()
    }
    override fun claimPublicPage(now: Instant, lease: Duration): ApplicationRequirement? {
        val row = jpa.nextPublicPageForUpdate(now) ?: return null
        row.status = RequirementStatus.FETCHING_PUBLIC.name
        row.attempts++
        row.leaseToken = UUID.randomUUID().toString()
        row.leaseExpiresAt = now.plus(lease)
        row.updatedAt = now
        return jpa.saveAndFlush(row).domain()
    }
    override fun renew(id: String, token: String, now: Instant, lease: Duration) = jpa.renew(id, token, now, now.plus(lease)) == 1
    override fun renewPublicPage(id: String, token: String, now: Instant, lease: Duration) = jpa.renewPublicPage(id, token, now, now.plus(lease)) == 1
}
