package com.youngwoo.jobmoa.applicationrequirements.application

import com.youngwoo.jobmoa.applicationrequirements.domain.*
import com.youngwoo.jobmoa.crawler.domain.model.PostingUrl
import com.youngwoo.jobmoa.posting.application.PostingService
import com.youngwoo.jobmoa.shared.LeaseLost
import com.youngwoo.jobmoa.shared.ResourceNotFound
import com.youngwoo.jobmoa.workspace.application.WorkspaceAccess
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class ApplicationRequirementService(
    private val requirements: ApplicationRequirementRepository,
    private val access: WorkspaceAccess,
    private val postings: PostingService,
) {
    @Transactional(readOnly = true)
    fun list(postingId: Long): List<ApplicationRequirementView> {
        postings.requirePosting(postingId)
        return requirements.findByPosting(postingId).map(::view)
    }

    /** 사용자가 붙여넣거나 수정한 원문을 새 버전으로 저장한다. */
    @Transactional
    fun create(postingId: Long, source: RequirementSource, sourceUrl: String?, rawText: String): ApplicationRequirementView {
        access.writing()
        postings.requirePosting(postingId)
        require(source == RequirementSource.USER_PASTED) { "자동 확인된 지원 페이지 원문은 이 화면에서 직접 입력할 수 없습니다." }
        val text = rawText.trim()
        require(text.isNotEmpty() && text.length <= MAX_TEXT_CHARS) { "지원서 원문은 1자 이상 ${MAX_TEXT_CHARS}자 이하여야 합니다." }
        if (!requirements.lockPosting(postingId)) throw ResourceNotFound("공고를 찾을 수 없습니다.")
        latestSavedVersion(postingId)?.takeIf { it.rawText == text }?.let { return view(it) }
        val safeUrl = sourceUrl?.trim()?.takeIf(String::isNotBlank)?.also {
            require(it.length <= 2048) { "출처 URL이 너무 깁니다." }
            PostingUrl.normalize(it)
        }
        val now = Instant.now()
        val requirement = ApplicationRequirement(
            id = UUID.randomUUID().toString(), postingId = postingId, source = source,
            sourceUrl = safeUrl, rawText = text, status = RequirementStatus.SAVED,
            attempts = 0, errorMessage = null, leaseToken = null, leaseExpiresAt = null,
            createdAt = now, updatedAt = now, versionSaved = true,
        )
        val version = requirement.copy(contentVersionId = requirement.id)
        requirements.save(version)
        return view(version)
    }

    private fun queuePublicPage(posting: com.youngwoo.jobmoa.posting.domain.model.Posting, collectionRunId: String): ApplicationRequirementView {
        PostingUrl.normalize(posting.url)
        requirements.findByCollectionRunAndPosting(collectionRunId, posting.id)?.let { return view(it) }
        val now = Instant.now()
        val requirement = ApplicationRequirement(
            id = UUID.randomUUID().toString(), postingId = posting.id, source = RequirementSource.PUBLIC_PAGE,
            sourceUrl = posting.url, rawText = "", status = RequirementStatus.WAITING_PUBLIC,
            attempts = 0, errorMessage = null, leaseToken = null, leaseExpiresAt = null,
            createdAt = now, updatedAt = now, collectionRunId = collectionRunId,
        )
        requirements.save(requirement)
        return view(requirement)
    }

    /** 열린 공고마다 현재 수집 실행에 연결된 지원 페이지 확인 이력을 만든다. */
    @Transactional
    fun collectAfterPostingCrawl(postingId: Long, collectionRunId: String): ApplicationRequirementView? {
        access.writing()
        val posting = postings.requirePosting(postingId)
        if (posting.closed) return null
        return queuePublicPage(posting, collectionRunId)
    }

    @Transactional(readOnly = true)
    fun collectionProgress(runId: String): Map<String, Int> {
        val counts = requirements.countByCollectionRun(runId)
        fun count(status: RequirementStatus) = counts.filter { it.status == status }.sumOf { it.total }
        return mapOf(
            "total" to counts.sumOf { it.total },
            "pending" to count(RequirementStatus.WAITING_PUBLIC),
            "processing" to count(RequirementStatus.FETCHING_PUBLIC),
            "fetched" to counts.filter { it.publicPageFetched }.sumOf { it.total },
            "failed" to counts.filter { it.status == RequirementStatus.FAILED && !it.publicPageFetched }.sumOf { it.total },
        )
    }

    @Transactional(readOnly = true)
    fun collectionSummary(runId: String): List<ApplicationRequirementRunSummary> = requirements.summarizeCollectionRun(runId)

    @Transactional(readOnly = true)
    fun latestPublicSummary(): List<ApplicationRequirementRunSummary> = requirements.latestPublicSummary()

    @Transactional
    fun claimPublicPage(): PublicPageFetchLease? {
        access.writing()
        val row = requirements.claimPublicPage(Instant.now(), PUBLIC_FETCH_LEASE) ?: return null
        return PublicPageFetchLease(row.id, row.postingId, requireNotNull(row.leaseToken), requireNotNull(row.sourceUrl))
    }

    @Transactional
    fun renewPublicPage(id: String, token: String) = requirements.renewPublicPage(id, token, Instant.now(), PUBLIC_FETCH_LEASE)

    @Transactional
    fun completePublicPage(id: String, token: String, resolvedUrl: String, text: String) {
        access.writing()
        val current = requirements.lock(id) ?: throw ResourceNotFound("지원서 정보를 찾을 수 없습니다.")
        requirePublicLease(current, token)
        val body = text.trim().take(MAX_TEXT_CHARS)
        require(body.length >= MIN_PUBLIC_TEXT_CHARS) { "공개 지원 페이지에서 읽을 수 있는 문항 텍스트가 충분하지 않습니다." }
        if (!requirements.lockPosting(current.postingId)) throw ResourceNotFound("공고를 찾을 수 없습니다.")
        val safeResolvedUrl = PostingUrl.normalize(resolvedUrl)
        val previousVersion = latestSavedVersion(current.postingId)
        val sameAsLatest = previousVersion?.rawText == body
        requirements.save(current.copy(sourceUrl = safeResolvedUrl, rawText = if (sameAsLatest) "" else body,
            status = RequirementStatus.SAVED, errorMessage = null, leaseToken = null, leaseExpiresAt = null,
            publicPageFetched = true, updatedAt = Instant.now(), versionSaved = !sameAsLatest,
            contentVersionId = if (sameAsLatest) previousVersion.id else current.id))
    }

    @Transactional
    fun failPublicPage(id: String, token: String, message: String) {
        access.writing()
        val current = requirements.lock(id) ?: throw ResourceNotFound("지원서 정보를 찾을 수 없습니다.")
        requirePublicLease(current, token)
        requirements.save(current.copy(status = RequirementStatus.FAILED, errorMessage = safeError(message),
            leaseToken = null, leaseExpiresAt = null, updatedAt = Instant.now()))
    }

    private fun requirePublicLease(row: ApplicationRequirement, token: String) {
        if (row.status != RequirementStatus.FETCHING_PUBLIC || row.leaseToken != token || row.leaseExpiresAt?.isAfter(Instant.now()) != true) throw LeaseLost()
    }

    private fun latestSavedVersion(postingId: Long): ApplicationRequirement? =
        requirements.latestSavedVersionForUpdate(postingId)

    private fun view(row: ApplicationRequirement) = ApplicationRequirementView(
        row.id, row.postingId, row.source, row.sourceUrl, row.rawText, row.status, row.attempts,
        row.errorMessage, row.createdAt, row.updatedAt, row.collectionRunId, row.publicPageFetched,
        row.versionSaved, row.contentVersionId,
    )

    private fun safeError(value: String) = value
        .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~-]+"), "Bearer [숨김]")
        .trim().take(1800)

    companion object {
        const val MAX_TEXT_CHARS = 100_000
        const val MIN_PUBLIC_TEXT_CHARS = 40
        private val PUBLIC_FETCH_LEASE = Duration.ofMinutes(2)
    }
}

data class ApplicationRequirementView(
    val id: String,
    val postingId: Long,
    val source: RequirementSource,
    val sourceUrl: String?,
    val rawText: String,
    val status: RequirementStatus,
    val attempts: Int,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val collectionRunId: String?,
    val publicPageFetched: Boolean,
    val versionSaved: Boolean,
    val contentVersionId: String?,
)
