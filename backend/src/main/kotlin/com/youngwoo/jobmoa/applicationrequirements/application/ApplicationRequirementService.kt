package com.youngwoo.jobmoa.applicationrequirements.application

import com.youngwoo.jobmoa.applicationrequirements.domain.*
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
    fun confirm(id: String): ApplicationRequirementView {
        access.writing()
        val current = requirements.lock(id) ?: throw ResourceNotFound("지원서 정보를 찾을 수 없습니다.")
        require(current.status == RequirementStatus.REVIEW && current.resultJson != null) { "AI 분석 결과를 확인한 뒤 확정할 수 있습니다." }
        val updated = current.copy(status = RequirementStatus.CONFIRMED, confirmedAt = Instant.now(), updatedAt = Instant.now())
        requirements.save(updated)
        return view(updated)
    }

    @Transactional
    fun retry(id: String): ApplicationRequirementView {
        access.writing()
        val current = requirements.lock(id) ?: throw ResourceNotFound("지원서 정보를 찾을 수 없습니다.")
        require(current.status == RequirementStatus.FAILED) { "분석 실패한 항목만 다시 분석할 수 있습니다." }
        val updated = current.copy(status = RequirementStatus.WAITING_AI, errorMessage = null, leaseToken = null, leaseExpiresAt = null, updatedAt = Instant.now())
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

    private fun view(row: ApplicationRequirement) = ApplicationRequirementView(row.id, row.postingId, row.source,
        row.sourceUrl, row.rawText, row.resultJson?.let { mapper.readTree(it) }, row.status, row.attempts,
        row.errorMessage, row.createdAt, row.updatedAt, row.confirmedAt)

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
        private const val MAX_ITEMS = 100
        private val LEASE = Duration.ofMinutes(10)
    }
}

data class ApplicationRequirementLease(val taskId: String, val leaseToken: String, val postingId: Long,
    val source: String, val sourceUrl: String?, val rawText: String)
data class ApplicationRequirementView(val id: String, val postingId: Long, val source: RequirementSource,
    val sourceUrl: String?, val rawText: String, val result: tools.jackson.databind.JsonNode?,
    val status: RequirementStatus, val attempts: Int, val errorMessage: String?, val createdAt: Instant,
    val updatedAt: Instant, val confirmedAt: Instant?)
