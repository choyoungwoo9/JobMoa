package com.youngwoo.jobmoa.applicationrequirements.application

import com.youngwoo.jobmoa.applicationrequirements.domain.*
import com.youngwoo.jobmoa.crawler.domain.model.PostingUrl
import com.youngwoo.jobmoa.shared.LeaseLost
import com.youngwoo.jobmoa.shared.ResourceNotFound
import com.youngwoo.jobmoa.posting.application.PostingService
import com.youngwoo.jobmoa.workspace.application.WorkspaceAccess
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class ApplicationRequirementService(
    private val requirements: ApplicationRequirementRepository,
    private val access: WorkspaceAccess,
    private val postings: PostingService,
    private val mapper: JsonMapper,
) {
    @Transactional(readOnly = true)
    fun list(postingId: Long): List<ApplicationRequirementView> {
        postings.requirePosting(postingId)
        return requirements.findByPosting(postingId).map(::view)
    }

    @Transactional
    fun create(postingId: Long, source: RequirementSource, sourceUrl: String?, rawText: String): ApplicationRequirementView {
        access.writing()
        postings.requirePosting(postingId)
        require(source == RequirementSource.USER_PASTED) { "실제 지원 페이지 원문은 수집으로 확인하고, 직접 등록할 때는 지원 페이지 문항만 붙여넣어 주세요." }
        val text = rawText.trim()
        require(text.length in 20..MAX_TEXT_CHARS) { "분석할 텍스트는 20자 이상 ${MAX_TEXT_CHARS}자 이하여야 합니다." }
        val now = Instant.now()
        val requirement = ApplicationRequirement(UUID.randomUUID().toString(), postingId, source,
            sourceUrl?.trim()?.takeIf(String::isNotBlank)?.also { require(it.length <= 2048) { "출처 URL이 너무 깁니다." } },
            text, null, RequirementStatus.WAITING_AI, 0, null, null, null, now, now, null)
        requirements.save(requirement)
        return view(requirement)
    }

    @Transactional
    fun collectPublicPage(postingId: Long): ApplicationRequirementView {
        access.writing()
        val posting = postings.requirePosting(postingId)
        return queuePublicPage(posting)
    }

    private fun queuePublicPage(posting: com.youngwoo.jobmoa.posting.domain.model.Posting, collectionRunId: String? = null): ApplicationRequirementView {
        PostingUrl.normalize(posting.url)
        if (collectionRunId == null) {
            requirements.findByPosting(posting.id).firstOrNull { it.source == RequirementSource.PUBLIC_PAGE &&
                it.status in setOf(RequirementStatus.WAITING_PUBLIC, RequirementStatus.FETCHING_PUBLIC, RequirementStatus.WAITING_AI, RequirementStatus.ANALYZING) }
                ?.let { return view(it) }
        } else {
            requirements.findByCollectionRunAndPosting(collectionRunId, posting.id)
                ?.let { return view(it) }
        }
        val now = Instant.now()
        val requirement = ApplicationRequirement(UUID.randomUUID().toString(), posting.id, RequirementSource.PUBLIC_PAGE,
            posting.url, "", null, RequirementStatus.WAITING_PUBLIC, 0, null, null, null, now, now, null,
            collectionRunId, false)
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
        val fetched = counts.filter { it.publicPageFetched }.sumOf { it.total }
        return mapOf(
            "total" to counts.sumOf { it.total },
            "pending" to count(RequirementStatus.WAITING_PUBLIC),
            "processing" to count(RequirementStatus.FETCHING_PUBLIC),
            "fetched" to fetched,
            "failed" to counts.filter { it.status == RequirementStatus.FAILED && !it.publicPageFetched }.sumOf { it.total },
            "analysisPending" to counts.filter { it.publicPageFetched && it.status in setOf(RequirementStatus.WAITING_AI, RequirementStatus.ANALYZING) }.sumOf { it.total },
            "analysisFailed" to counts.filter { it.publicPageFetched && it.status == RequirementStatus.FAILED }.sumOf { it.total },
            "review" to counts.filter { it.status in setOf(RequirementStatus.REVIEW, RequirementStatus.CONFIRMED) }.sumOf { it.total },
        )
    }

    @Transactional(readOnly = true)
    fun collectionSummary(runId: String): List<ApplicationRequirementRunSummary> = requirements.summarizeCollectionRun(runId)

    @Transactional(readOnly = true)
    fun latestPublicSummary(): List<ApplicationRequirementRunSummary> = requirements.latestPublicSummary()

    @Transactional
    fun confirm(id: String): ApplicationRequirementView {
        access.writing()
        val current = requirements.lock(id) ?: throw ResourceNotFound("지원서 정보를 찾을 수 없습니다.")
        require(current.status == RequirementStatus.REVIEW && current.resultJson != null) { "AI 분석 결과를 확인한 뒤 확정할 수 있습니다." }
        require(current.source != RequirementSource.POSTING_BODY && (current.source != RequirementSource.PUBLIC_PAGE || current.publicPageFetched)) {
            "실제 지원 페이지 또는 직접 입력한 문항에서 만든 결과만 확정할 수 있습니다."
        }
        val updated = current.copy(status = RequirementStatus.CONFIRMED, confirmedAt = Instant.now(), updatedAt = Instant.now())
        requirements.save(updated)
        return view(updated)
    }

    @Transactional
    fun retry(id: String): ApplicationRequirementView {
        access.writing()
        val current = requirements.lock(id) ?: throw ResourceNotFound("지원서 정보를 찾을 수 없습니다.")
        require(current.status == RequirementStatus.FAILED) { "분석 실패한 항목만 다시 분석할 수 있습니다." }
        require(current.source != RequirementSource.POSTING_BODY) { "공고 본문은 지원 항목 분석에 사용할 수 없습니다. 실제 지원 페이지를 다시 확인하거나 문항을 직접 붙여넣어 주세요." }
        val updated = current.copy(status = if (current.source == RequirementSource.PUBLIC_PAGE && current.rawText.isBlank()) RequirementStatus.WAITING_PUBLIC else RequirementStatus.WAITING_AI,
            errorMessage = null, leaseToken = null, leaseExpiresAt = null, updatedAt = Instant.now())
        requirements.save(updated)
        return view(updated)
    }

    @Transactional
    fun claim(): ApplicationRequirementLease? {
        access.writing()
        val row = requirements.claim(Instant.now(), LEASE) ?: return null
        return ApplicationRequirementLease(row.id, requireNotNull(row.leaseToken), row.postingId, row.source.name,
            row.sourceUrl, row.rawText.take(MAX_TEXT_CHARS))
    }

    @Transactional
    fun renew(id: String, token: String) = requirements.renew(id, token, Instant.now(), LEASE)

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
        val safeResolvedUrl = PostingUrl.normalize(resolvedUrl)
        requirements.save(current.copy(sourceUrl = safeResolvedUrl, rawText = body, status = RequirementStatus.WAITING_AI,
            errorMessage = null, leaseToken = null, leaseExpiresAt = null, publicPageFetched = true, updatedAt = Instant.now()))
    }

    @Transactional
    fun failPublicPage(id: String, token: String, message: String) {
        access.writing()
        val current = requirements.lock(id) ?: throw ResourceNotFound("지원서 정보를 찾을 수 없습니다.")
        requirePublicLease(current, token)
        requirements.save(current.copy(status = RequirementStatus.FAILED, errorMessage = safeError(message),
            leaseToken = null, leaseExpiresAt = null, updatedAt = Instant.now()))
    }

    @Transactional
    fun complete(id: String, token: String, result: Map<String, Any?>) {
        access.writing()
        val current = requirements.lock(id) ?: throw ResourceNotFound("지원서 정보를 찾을 수 없습니다.")
        requireLease(current, token)
        val json = mapper.writeValueAsString(sanitize(result, current.rawText))
        requirements.save(current.copy(status = RequirementStatus.REVIEW, resultJson = json, errorMessage = null,
            leaseToken = null, leaseExpiresAt = null, updatedAt = Instant.now()))
    }

    @Transactional
    fun fail(id: String, token: String, message: String) {
        access.writing()
        val current = requirements.lock(id) ?: throw ResourceNotFound("지원서 정보를 찾을 수 없습니다.")
        requireLease(current, token)
        requirements.save(current.copy(status = RequirementStatus.FAILED, errorMessage = safeError(message),
            leaseToken = null, leaseExpiresAt = null, updatedAt = Instant.now()))
    }

    private fun requireLease(row: ApplicationRequirement, token: String) {
        if (row.status != RequirementStatus.ANALYZING || row.leaseToken != token || row.leaseExpiresAt?.isAfter(Instant.now()) != true) throw LeaseLost()
    }

    private fun requirePublicLease(row: ApplicationRequirement, token: String) {
        if (row.status != RequirementStatus.FETCHING_PUBLIC || row.leaseToken != token || row.leaseExpiresAt?.isAfter(Instant.now()) != true) throw LeaseLost()
    }

    private fun view(row: ApplicationRequirement) = ApplicationRequirementView(row.id, row.postingId, row.source,
        row.sourceUrl, row.rawText, row.resultJson?.let { mapper.readTree(it) }, row.status, row.attempts,
        row.errorMessage, row.createdAt, row.updatedAt, row.confirmedAt, row.collectionRunId, row.publicPageFetched)

    private fun sanitize(result: Map<String, Any?>, sourceText: String): Map<String, Any?> {
        fun evidence(value: Any?): String? = value?.toString()?.trim()?.take(1000)?.takeIf { quote ->
            quote.isNotBlank() && normalized(sourceText).contains(normalized(quote))
        }
        fun certainty(value: Any?) = value?.toString()?.takeIf { it in setOf("REQUIRED", "OPTIONAL") } ?: "UNKNOWN"
        val documents = (result["documents"] as? List<*>)?.take(MAX_ITEMS)?.mapNotNull { item ->
            (item as? Map<*, *>)?.let { row ->
                val quote = evidence(row["evidence"]) ?: return@mapNotNull null
                val name = row["name"]?.toString()?.trim()?.take(300).orEmpty()
                if (name.isBlank()) return@mapNotNull null
                mapOf("name" to name, "requirement" to certainty(row["requirement"]), "format" to (row["format"]?.toString()?.take(300) ?: ""), "notes" to (row["notes"]?.toString()?.take(2000) ?: ""), "evidence" to quote)
            }
        }.orEmpty()
        val questions = (result["questions"] as? List<*>)?.take(MAX_ITEMS)?.mapNotNull { item ->
            (item as? Map<*, *>)?.let { row ->
                val quote = evidence(row["evidence"]) ?: return@mapNotNull null
                val prompt = row["prompt"]?.toString()?.trim()?.take(8000).orEmpty()
                if (prompt.isBlank() || !normalized(sourceText).contains(normalized(prompt))) return@mapNotNull null
                mapOf("prompt" to prompt, "required" to certainty(row["required"]), "answerType" to (row["answerType"]?.toString()?.take(100) ?: "UNKNOWN"),
                    "minLength" to safeLength(row["minLength"]), "maxLength" to safeLength(row["maxLength"]), "instructions" to (row["instructions"]?.toString()?.take(2000) ?: ""), "evidence" to quote)
            }
        }.orEmpty()
        return mapOf("documents" to documents, "questions" to questions,
            "notes" to (result["notes"]?.toString()?.take(4000) ?: ""),
            "confidence" to (result["confidence"]?.toString()?.takeIf { it in setOf("HIGH", "MEDIUM") } ?: "LOW"))
    }

    private fun normalized(value: String) = value.replace(Regex("\\s+"), " ").trim()
    private fun safeLength(value: Any?) = (value as? Number)?.toInt()?.takeIf { it in 0..1_000_000 }

    private fun safeError(value: String) = value.replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~-]+"), "Bearer [숨김]").trim().take(1800)

    companion object {
        const val MAX_TEXT_CHARS = 100_000
        const val MIN_PUBLIC_TEXT_CHARS = 40
        private const val MAX_ITEMS = 100
        private val LEASE = Duration.ofMinutes(10)
        private val PUBLIC_FETCH_LEASE = Duration.ofMinutes(2)
    }
}

data class ApplicationRequirementLease(val taskId: String, val leaseToken: String, val postingId: Long,
    val source: String, val sourceUrl: String?, val rawText: String)
data class ApplicationRequirementView(val id: String, val postingId: Long, val source: RequirementSource,
    val sourceUrl: String?, val rawText: String, val result: tools.jackson.databind.JsonNode?,
    val status: RequirementStatus, val attempts: Int, val errorMessage: String?, val createdAt: Instant,
    val updatedAt: Instant, val confirmedAt: Instant?, val collectionRunId: String?, val publicPageFetched: Boolean)
