package com.youngwoo.jobmoa.career.application

import com.youngwoo.jobmoa.aiqa.application.AiWorkerPresence
import com.youngwoo.jobmoa.career.domain.model.*
import com.youngwoo.jobmoa.career.domain.repository.CareerMaterialRepository
import com.youngwoo.jobmoa.shared.CrawlerProperties
import com.youngwoo.jobmoa.shared.LeaseLost
import com.youngwoo.jobmoa.shared.ResourceNotFound
import com.youngwoo.jobmoa.workspace.application.WorkspaceAccess
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class CareerMaterialService(
    private val repository: CareerMaterialRepository,
    private val extractor: CareerDocumentTextExtractor,
    private val properties: CrawlerProperties,
    private val access: WorkspaceAccess,
    private val workerPresence: AiWorkerPresence,
) {
    @Transactional
    fun upload(files: List<MultipartFile>): List<CareerMaterialSummary> {
        require(files.isNotEmpty()) { "파일을 하나 이상 선택하세요." }
        require(files.size <= MAX_FILES_PER_UPLOAD) { "한 번에 최대 ${MAX_FILES_PER_UPLOAD}개까지 올릴 수 있습니다." }
        require(files.sumOf { it.size } <= MAX_UPLOAD_BYTES) { "한 번에 올릴 수 있는 용량은 ${MAX_UPLOAD_BYTES / (1024 * 1024)}MB까지입니다." }
        access.writing()
        val directory = storageDirectory()
        Files.createDirectories(directory)
        val createdFiles = mutableListOf<Path>()
        try {
            val results = files.map { file ->
                require(file.size in 1..MAX_FILE_BYTES) { "파일은 1바이트 이상 ${MAX_FILE_BYTES / (1024 * 1024)}MB 이하여야 합니다: ${safeName(file.originalFilename)}" }
                val id = UUID.randomUUID().toString()
                val temporary = Files.createTempFile(directory, "upload-", ".tmp")
                try {
                    file.inputStream.use { input -> Files.newOutputStream(temporary).use(input::copyTo) }
                    val size = Files.size(temporary)
                    require(size in 1..MAX_FILE_BYTES) { "업로드 파일 크기가 허용 범위를 벗어났습니다." }
                    val hash = sha256(temporary)
                    val previous = repository.findByHash(hash)
                    if (previous != null) return@map summary(previous, duplicate = true)

                    val storageKey = id
                    val storedPath = directory.resolve("$storageKey.bin")
                    Files.move(temporary, storedPath)
                    createdFiles.add(storedPath)
                    if (createdFiles.size == 1) {
                        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                            override fun afterCompletion(status: Int) {
                                if (status != TransactionSynchronization.STATUS_COMMITTED) createdFiles.forEach { runCatching { Files.deleteIfExists(it) } }
                            }
                        })
                    }
                    val originalName = safeName(file.originalFilename)
                    val extracted = runCatching { extractor.extract(storedPath, originalName) }
                    val document = extracted.getOrNull()
                    val fragments = document?.fragments.orEmpty().filter { it.content.isNotBlank() }
                    val now = Instant.now()
                    val material = CareerMaterial(
                        id = id,
                        originalFilename = originalName,
                        detectedMediaType = document?.mediaType ?: "application/octet-stream",
                        fileSize = size,
                        contentHash = hash,
                        storageKey = storageKey,
                        status = if (fragments.isEmpty()) CareerMaterialStatus.UNREADABLE else CareerMaterialStatus.WAITING_AI,
                        errorMessage = when {
                            extracted.isFailure -> safeError(extracted.exceptionOrNull()?.message ?: "문서에서 텍스트를 읽지 못했습니다.")
                            fragments.isEmpty() -> "텍스트를 추출하지 못했습니다. 이미지 전용 파일이거나 읽을 수 없는 형식일 수 있습니다. 원본 파일은 보관했습니다."
                            document?.truncated == true -> "문서가 너무 커 텍스트 일부만 추출했습니다. 추출된 범위부터 분석합니다."
                            else -> null
                        },
                        createdAt = now,
                        updatedAt = now,
                    )
                    repository.save(material)
                    repository.saveFragments(fragments.map { fragment ->
                        CareerMaterialFragment(UUID.randomUUID().toString(), id, fragment.ordinal, fragment.locationLabel.take(255), fragment.content)
                    })
                    summary(material, duplicate = false)
                } finally {
                    Files.deleteIfExists(temporary)
                }
            }
            return results
        } catch (error: Exception) {
            createdFiles.forEach { runCatching { Files.deleteIfExists(it) } }
            throw error
        }
    }

    /** 사용자가 인터뷰 경험을 경력 자료로 승인하면 답변 원문을 로컬 텍스트 자료로 보존한다. */
    @Transactional
    fun saveInterviewFact(title: String, summary: String, sourceExcerpt: String): CareerFact {
        access.writing()
        val cleanTitle = title.trim()
        val cleanSummary = summary.trim()
        val quote = sourceExcerpt.trim()
        require(cleanTitle.isNotBlank() && cleanTitle.length <= 500) { "경험 제목은 1~500자로 입력하세요." }
        require(cleanSummary.isNotBlank() && cleanSummary.length <= 8000) { "경험 설명은 1~8,000자로 입력하세요." }
        require(quote.isNotBlank() && quote.length <= 1000) { "원문 근거는 1~1,000자로 입력하세요." }
        val id = UUID.randomUUID().toString()
        val now = Instant.now()
        val content = "경험 인터뷰에서 승인한 답변 근거\n\n$quote\n"
        val directory = storageDirectory()
        Files.createDirectories(directory)
        val path = directory.resolve("$id.bin")
        val temporary = Files.createTempFile(directory, "interview-", ".tmp")
        try {
            Files.writeString(temporary, content)
            val size = Files.size(temporary)
            val hash = sha256(temporary)
            Files.move(temporary, path)
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) runCatching { Files.deleteIfExists(path) }
                }
            })
            repository.save(CareerMaterial(id, "경험 인터뷰.txt", "text/plain", size, hash, id, CareerMaterialStatus.ANALYZED,
                createdAt = now, updatedAt = now))
            val fragmentId = UUID.randomUUID().toString()
            repository.saveFragments(listOf(CareerMaterialFragment(fragmentId, id, 0, "경험 인터뷰 답변", content)))
            val fact = CareerFact(UUID.randomUUID().toString(), id, CareerFactKind.EXPERIENCE, cleanTitle, cleanSummary,
                CareerFactStatus.UNVERIFIED, listOf(CareerFactEvidence(UUID.randomUUID().toString(), fragmentId, "경험 인터뷰 답변", quote)), now, now)
            repository.replaceFacts(id, listOf(fact), now)
            return fact
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    @Transactional(readOnly = true)
    fun list(): List<CareerMaterialSummary> = repository.list().map { summary(it, false) }

    @Transactional(readOnly = true)
    fun detail(id: String): CareerMaterialDetail {
        val material = repository.find(id) ?: throw ResourceNotFound("경력 자료를 찾을 수 없습니다.")
        return CareerMaterialDetail(summary(material, false), repository.fragments(id), repository.facts(id))
    }

    @Transactional
    fun updateFact(materialId: String, factId: String, title: String, summary: String, status: CareerFactStatus): CareerFact {
        access.writing()
        val material = repository.lock(materialId) ?: throw ResourceNotFound("경력 자료를 찾을 수 없습니다.")
        require(material.status == CareerMaterialStatus.ANALYZED) { "분석이 끝난 경력 자료만 검토할 수 있습니다." }
        val current = repository.facts(materialId).firstOrNull { it.id == factId }
            ?: throw ResourceNotFound("경력 사실 후보를 찾을 수 없습니다.")
        val cleanTitle = title.trim()
        val cleanSummary = summary.trim()
        require(cleanTitle.isNotBlank() && cleanTitle.length <= 500) { "제목은 1~500자로 입력하세요." }
        require(cleanSummary.isNotBlank() && cleanSummary.length <= 8000) { "설명은 1~8,000자로 입력하세요." }
        require(current.evidence.isNotEmpty()) { "원문 근거가 있는 경력 정보만 저장할 수 있습니다." }
        val updated = current.copy(title = cleanTitle, summary = cleanSummary, status = status, updatedAt = Instant.now())
        repository.saveFact(updated)
        return repository.facts(materialId).first { it.id == factId }
    }

    @Transactional(readOnly = true)
    fun originalPath(id: String): Pair<Path, String> {
        val material = repository.find(id) ?: throw ResourceNotFound("경력 자료를 찾을 수 없습니다.")
        val path = storageDirectory().resolve("${material.storageKey}.bin")
        if (!Files.isRegularFile(path)) throw ResourceNotFound("원본 파일을 찾을 수 없습니다.")
        return path to material.originalFilename
    }

    @Transactional
    fun delete(id: String) {
        access.writing()
        val material = repository.lock(id) ?: throw ResourceNotFound("경력 자료를 찾을 수 없습니다.")
        repository.delete(id)
        val original = storageDirectory().resolve("${material.storageKey}.bin")
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() { runCatching { Files.deleteIfExists(original) } }
        })
    }

    @Transactional
    fun retry(id: String): CareerMaterialSummary {
        access.writing()
        val material = repository.lock(id) ?: throw ResourceNotFound("경력 자료를 찾을 수 없습니다.")
        require(material.status == CareerMaterialStatus.FAILED) { "분석 실패한 자료만 다시 분석할 수 있습니다." }
        require(repository.fragments(id).isNotEmpty()) { "추출된 텍스트가 없어 분석을 다시 시작할 수 없습니다." }
        val updated = material.copy(status = CareerMaterialStatus.WAITING_AI, errorMessage = null, leaseToken = null, leaseExpiresAt = null, updatedAt = Instant.now())
        repository.save(updated)
        return summary(updated, false)
    }

    @Transactional
    fun claim(): CareerMaterialLease? {
        access.writing()
        val lease = repository.claimNext(Instant.now(), TASK_LEASE) ?: return null
        var remaining = MAX_ANALYSIS_CHARS
        val bounded = lease.fragments.mapNotNull { fragment ->
            if (remaining <= 0) return@mapNotNull null
            val content = fragment.content.take(remaining)
            remaining -= content.length
            fragment.copy(content = content).takeIf { content.isNotBlank() }
        }
        return lease.copy(fragments = bounded)
    }

    @Transactional
    fun renew(id: String, token: String): Boolean {
        access.writing()
        return repository.renew(id, token, Instant.now(), TASK_LEASE)
    }

    @Transactional
    fun complete(id: String, token: String, result: CareerAnalysisResult) {
        access.writing()
        val material = repository.lock(id) ?: throw ResourceNotFound("경력 자료를 찾을 수 없습니다.")
        requireLease(material, token)
        val fragments = repository.fragments(id).associateBy { it.id }
        val facts = result.facts.take(MAX_FACTS_PER_MATERIAL).mapNotNull { draft ->
            val evidence = draft.evidence.distinctBy { it.fragmentId }.mapNotNull { candidate ->
                val source = fragments[candidate.fragmentId] ?: return@mapNotNull null
                val quote = candidate.excerpt.trim().take(1000)
                if (quote.isBlank() || !containsNormalized(source.content, quote)) return@mapNotNull null
                CareerFactEvidence(UUID.randomUUID().toString(), source.id, source.locationLabel, quote)
            }
            if (evidence.isEmpty()) return@mapNotNull null
            CareerFact(
                id = UUID.randomUUID().toString(), materialId = id, kind = draft.kind,
                title = draft.title.trim().take(500).ifBlank { return@mapNotNull null },
                summary = draft.summary.trim().take(8000).ifBlank { return@mapNotNull null },
                status = CareerFactStatus.UNVERIFIED, evidence = evidence,
                createdAt = Instant.now(), updatedAt = Instant.now(),
            )
        }
        val now = Instant.now()
        repository.replaceFacts(id, facts, now)
        repository.save(material.copy(status = CareerMaterialStatus.ANALYZED, errorMessage = null, leaseToken = null, leaseExpiresAt = null, updatedAt = now))
    }

    @Transactional
    fun fail(id: String, token: String, message: String) {
        access.writing()
        val material = repository.lock(id) ?: throw ResourceNotFound("경력 자료를 찾을 수 없습니다.")
        requireLease(material, token)
        repository.save(material.copy(status = CareerMaterialStatus.FAILED, errorMessage = safeError(message), leaseToken = null, leaseExpiresAt = null, updatedAt = Instant.now()))
    }

    fun workerStatus() = workerPresence.status()

    private fun requireLease(material: CareerMaterial, token: String) {
        if (material.status != CareerMaterialStatus.ANALYZING || material.leaseToken != token) throw LeaseLost()
    }

    private fun summary(material: CareerMaterial, duplicate: Boolean) = CareerMaterialSummary(
        id = material.id,
        originalFilename = material.originalFilename,
        detectedMediaType = material.detectedMediaType,
        fileSize = material.fileSize,
        status = material.status,
        errorMessage = material.errorMessage,
        attempts = material.attempts,
        createdAt = material.createdAt,
        updatedAt = material.updatedAt,
        duplicate = duplicate,
    )

    private fun storageDirectory(): Path = properties.storageDirectory.resolve("career-materials").toAbsolutePath().normalize()

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeName(value: String?) = value.orEmpty().substringAfterLast('/').substringAfterLast('\\').trim().take(512).ifBlank { "이름 없는 파일" }
    private fun safeError(value: String) = value.replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~-]+"), "Bearer [숨김]").trim().take(1800)
    private fun containsNormalized(source: String, quote: String) = source.replace(Regex("\\s+"), " ").contains(quote.replace(Regex("\\s+"), " "))

    companion object {
        const val MAX_FILES_PER_UPLOAD = 20
        const val MAX_FILE_BYTES = 50L * 1024 * 1024
        const val MAX_UPLOAD_BYTES = 200L * 1024 * 1024
        const val MAX_FACTS_PER_MATERIAL = 100
        private const val MAX_ANALYSIS_CHARS = 160_000
        private val TASK_LEASE = Duration.ofMinutes(10)
    }
}

data class CareerMaterialSummary(
    val id: String,
    val originalFilename: String,
    val detectedMediaType: String,
    val fileSize: Long,
    val status: CareerMaterialStatus,
    val errorMessage: String?,
    val attempts: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val duplicate: Boolean,
)

data class CareerMaterialDetail(val material: CareerMaterialSummary, val fragments: List<CareerMaterialFragment>, val facts: List<CareerFact>)
