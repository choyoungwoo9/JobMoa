package com.youngwoo.jobmoa.career.assistant.application

import com.youngwoo.jobmoa.career.assistant.domain.model.*
import com.youngwoo.jobmoa.career.assistant.domain.repository.CareerAssistantTaskRepository
import com.youngwoo.jobmoa.career.domain.model.CareerFact
import com.youngwoo.jobmoa.career.domain.model.CareerFactStatus
import com.youngwoo.jobmoa.career.domain.repository.CareerMaterialRepository
import com.youngwoo.jobmoa.crawler.application.CompanyRegistry
import com.youngwoo.jobmoa.posting.domain.model.Posting
import com.youngwoo.jobmoa.posting.domain.repository.PostingRepository
import com.youngwoo.jobmoa.shared.LeaseLost
import com.youngwoo.jobmoa.shared.ResourceNotFound
import com.youngwoo.jobmoa.workspace.application.WorkspaceAccess
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class CareerAssistantService(
    private val tasks: CareerAssistantTaskRepository,
    private val materials: CareerMaterialRepository,
    private val postings: PostingRepository,
    private val companies: CompanyRegistry,
    private val access: WorkspaceAccess,
    private val mapper: JsonMapper,
) {
    @Transactional
    fun recommend(request: CareerRecommendationRequest): CareerAssistantTaskView {
        access.writing()
        require(request.count in 1..MAX_RECOMMENDATIONS) { "추천 개수는 1~${MAX_RECOMMENDATIONS}개로 설정하세요." }
        require(request.companyKeywords.length <= 200 && request.locationKeywords.length <= 200) { "회사·근무지 검색어는 각각 200자까지 입력할 수 있습니다." }
        val knownCategories = companies.categories.map { it.id }.toSet() - "all"
        require(request.categoryIds.all { it in knownCategories }) { "지원하지 않는 직군 필터가 포함되어 있습니다." }

        val allFacts = materials.verifiedFacts().filter { it.status == CareerFactStatus.VERIFIED }
        require(allFacts.isNotEmpty()) { "경력 자료에서 사실 후보를 먼저 확인·확정하세요." }
        val factContexts = allFacts.take(MAX_FACTS).map(::factPrompt)
        val categoryLabels = companies.categories.associate { it.id to it.name }
        val companyTerms = splitTerms(request.companyKeywords)
        val locationTerms = splitTerms(request.locationKeywords)
        val eligible = postings.list().asSequence()
            .filterNot { it.closed }
            .filter { request.categoryIds.isEmpty() || it.categories.any(request.categoryIds::contains) }
            .filter { companyTerms.isEmpty() || companyTerms.any { term -> it.company.contains(term, ignoreCase = true) } }
            .filter { locationTerms.isEmpty() || locationTerms.any { term -> it.location.contains(term, ignoreCase = true) } }
            .toList()
        require(eligible.isNotEmpty()) { "선택한 조건에 맞는 진행 중 공고가 없습니다." }

        val versions = postings.latestVersions(eligible.map { it.id }.distinct()).associateBy { it.postingId }
        val profileTokens = allFacts.flatMap { tokens("${it.title} ${it.summary}") }.toSet()
        val ranked = eligible.mapNotNull { posting ->
            val version = versions[posting.id] ?: return@mapNotNull null
            val categoryText = posting.categories.mapNotNull(categoryLabels::get).joinToString(" ")
            val titleTokens = tokens("${posting.title} $categoryText").toSet()
            val bodyTokens = tokens(version.bodyText).toSet()
            val score = titleTokens.intersect(profileTokens).size * 4 + bodyTokens.intersect(profileTokens).size
            RankedPosting(posting, version.bodyText, score)
        }.sortedWith(compareByDescending<RankedPosting> { it.score }.thenByDescending { it.posting.firstSeen })
        require(ranked.isNotEmpty()) { "본문 버전이 저장된 진행 중 공고가 없습니다." }

        val candidateContexts = ranked.take(MAX_CANDIDATES).map { candidate ->
            mapOf(
                "postingId" to candidate.posting.id,
                "company" to candidate.posting.company,
                "title" to candidate.posting.title,
                "location" to candidate.posting.location,
                "url" to candidate.posting.url,
                "categories" to candidate.posting.categories.toList().sorted(),
                "firstSeen" to candidate.posting.firstSeen,
                "body" to candidate.body.take(MAX_RECOMMENDATION_BODY_CHARS),
            )
        }
        val input = mapOf(
            "taskType" to CareerAssistantTaskType.RECOMMENDATIONS.name,
            "count" to request.count,
            "filters" to mapOf(
                "categoryIds" to request.categoryIds.toList().sorted(),
                "companyKeywords" to request.companyKeywords.trim(),
                "locationKeywords" to request.locationKeywords.trim(),
            ),
            "verifiedFactCount" to allFacts.size,
            "factsTruncated" to (allFacts.size > MAX_FACTS),
            "candidateCount" to ranked.size,
            "candidatesTruncated" to (ranked.size > MAX_CANDIDATES),
            "facts" to factContexts,
            "postings" to candidateContexts,
        )
        return saveNewTask(CareerAssistantTaskType.RECOMMENDATIONS, mapper.writeValueAsString(input))
    }

    @Transactional(readOnly = true)
    fun recentRecommendations(): List<CareerAssistantTaskView> =
        tasks.recent(CareerAssistantTaskType.RECOMMENDATIONS.name, MAX_RECENT_TASKS).map(::view)

    @Transactional(readOnly = true)
    fun readiness() = CareerAssistantReadiness(
        verifiedFactCount = materials.verifiedFacts().size,
        openPostingCount = postings.list().count { !it.closed },
    )

    @Transactional(readOnly = true)
    fun task(id: String): CareerAssistantTaskView = view(tasks.find(id) ?: throw ResourceNotFound("AI 경력 작업을 찾을 수 없습니다."))

    @Transactional(readOnly = true)
    fun drafts(recommendationId: String): List<CareerAssistantTaskView> {
        val parent = tasks.find(recommendationId) ?: throw ResourceNotFound("추천 결과를 찾을 수 없습니다.")
        require(parent.type == CareerAssistantTaskType.RECOMMENDATIONS) { "추천 결과가 아닙니다." }
        return tasks.drafts(recommendationId).map(::view)
    }

    @Transactional
    fun createDraft(recommendationId: String, postingId: Long): CareerAssistantTaskView {
        access.writing()
        val recommendationTask = tasks.find(recommendationId) ?: throw ResourceNotFound("추천 결과를 찾을 수 없습니다.")
        require(recommendationTask.type == CareerAssistantTaskType.RECOMMENDATIONS && recommendationTask.status == CareerAssistantTaskStatus.COMPLETED) {
            "완료된 추천 결과에서 공고를 선택하세요."
        }
        val existing = tasks.draft(recommendationId, postingId)
        if (existing != null) return view(existing)
        val selected = mapper.readTree(recommendationTask.resultJson ?: "{}")["recommendations"]
            ?.values()?.firstOrNull { it["posting"]?.get("id")?.asLong() == postingId }
            ?: throw ResourceNotFound("추천 목록에서 공고를 찾을 수 없습니다.")
        val posting = postings.find(postingId) ?: throw ResourceNotFound("공고를 찾을 수 없습니다.")
        require(!posting.closed) { "마감된 공고의 맞춤 자료는 만들 수 없습니다." }
        val version = postings.latestVersion(postingId) ?: throw ResourceNotFound("공고 본문을 찾을 수 없습니다.")
        val allFacts = materials.verifiedFacts().filter { it.status == CareerFactStatus.VERIFIED }
        require(allFacts.isNotEmpty()) { "확정된 경력 정보가 없어 맞춤 자료를 만들 수 없습니다." }
        val factContexts = allFacts.take(MAX_FACTS).map(::factPrompt)
        val input = mapOf(
            "taskType" to CareerAssistantTaskType.POSTING_DRAFT.name,
            "recommendation" to selected,
            "posting" to mapOf(
                "postingId" to posting.id,
                "company" to posting.company,
                "title" to posting.title,
                "location" to posting.location,
                "url" to posting.url,
                "categories" to posting.categories.toList().sorted(),
                "body" to version.bodyText.take(MAX_DRAFT_BODY_CHARS),
            ),
            "verifiedFactCount" to allFacts.size,
            "factsTruncated" to (allFacts.size > MAX_FACTS),
            "facts" to factContexts,
        )
        val now = Instant.now()
        val saved = tasks.save(CareerAssistantTask(
            id = UUID.randomUUID().toString(),
            type = CareerAssistantTaskType.POSTING_DRAFT,
            parentTaskId = recommendationId,
            postingId = postingId,
            status = CareerAssistantTaskStatus.WAITING_AI,
            requestJson = mapper.writeValueAsString(input),
            createdAt = now,
            updatedAt = now,
        ))
        return view(saved)
    }

    @Transactional
    fun retry(id: String): CareerAssistantTaskView {
        access.writing()
        val task = tasks.lock(id) ?: throw ResourceNotFound("AI 경력 작업을 찾을 수 없습니다.")
        require(task.status == CareerAssistantTaskStatus.FAILED) { "실패한 작업만 다시 시도할 수 있습니다." }
        return view(tasks.save(task.copy(
            status = CareerAssistantTaskStatus.WAITING_AI,
            resultJson = null,
            errorMessage = null,
            leaseToken = null,
            leaseExpiresAt = null,
            updatedAt = Instant.now(),
        )))
    }

    @Transactional
    fun claim(): CareerAssistantTaskLease? {
        access.writing()
        val task = tasks.claimNext(Instant.now(), TASK_LEASE) ?: return null
        return CareerAssistantTaskLease(task, requireNotNull(task.leaseToken))
    }

    @Transactional
    fun renew(id: String, token: String): Boolean {
        access.writing()
        return tasks.renew(id, token, Instant.now(), TASK_LEASE)
    }

    @Transactional
    fun complete(id: String, token: String, rawResult: JsonNode) {
        access.writing()
        val task = tasks.lock(id) ?: throw ResourceNotFound("AI 경력 작업을 찾을 수 없습니다.")
        requireLease(task, token)
        val result = when (task.type) {
            CareerAssistantTaskType.RECOMMENDATIONS -> normalizeRecommendations(task, rawResult)
            CareerAssistantTaskType.POSTING_DRAFT -> normalizeDraft(task, rawResult)
        }
        tasks.save(task.copy(
            status = CareerAssistantTaskStatus.COMPLETED,
            resultJson = mapper.writeValueAsString(result),
            errorMessage = null,
            leaseToken = null,
            leaseExpiresAt = null,
            updatedAt = Instant.now(),
        ))
    }

    @Transactional
    fun fail(id: String, token: String, message: String) {
        access.writing()
        val task = tasks.lock(id) ?: throw ResourceNotFound("AI 경력 작업을 찾을 수 없습니다.")
        requireLease(task, token)
        tasks.save(task.copy(
            status = CareerAssistantTaskStatus.FAILED,
            errorMessage = safeError(message),
            leaseToken = null,
            leaseExpiresAt = null,
            updatedAt = Instant.now(),
        ))
    }

    private fun normalizeRecommendations(task: CareerAssistantTask, raw: JsonNode): CareerRecommendationResult {
        val request = mapper.readTree(task.requestJson)
        val facts = factViews(request["facts"])
        val candidates = request["postings"]?.values()?.mapNotNull(::postingFromNode)
            ?.associateBy { it.id }.orEmpty()
        val limit = request["count"]?.asInt()?.coerceIn(1, MAX_RECOMMENDATIONS) ?: MAX_RECOMMENDATIONS
        val draft = mapper.treeToValue(raw, CareerRecommendationProposalResult::class.java)
        val items = draft.recommendations.distinctBy { it.postingId }.mapNotNull { proposal ->
            val candidate = candidates[proposal.postingId] ?: return@mapNotNull null
            if (postings.find(proposal.postingId)?.closed != false) return@mapNotNull null
            val evidence = proposal.verifiedFactIds.distinct().mapNotNull(facts::get)
            val fit = when (proposal.fit.uppercase()) {
                "STRONG_MATCH" -> "STRONG_MATCH"
                "POTENTIAL" -> "POTENTIAL"
                else -> "INSUFFICIENT_INFO"
            }
            val explanation = proposal.explanation.trim().take(1200)
            if (explanation.isBlank()) return@mapNotNull null
            CareerRecommendationItem(
                posting = candidate,
                fit = fit,
                explanation = explanation,
                matchingPoints = cleanLines(proposal.matchingPoints, 6, 500),
                gaps = cleanLines(proposal.gaps, 6, 500),
                evidence = evidence,
            )
        }.take(limit)
        return CareerRecommendationResult(items)
    }

    private fun normalizeDraft(task: CareerAssistantTask, raw: JsonNode): CareerPostingDraftResult {
        val request = mapper.readTree(task.requestJson)
        val factMap = factViews(request["facts"])
        val posting = postingFromNode(request["posting"] ?: mapper.readTree("{}"))
            ?: throw IllegalArgumentException("맞춤 자료의 공고 정보가 없습니다.")
        val proposal = mapper.treeToValue(raw, CareerPostingDraftProposal::class.java)
        fun evidence(ids: List<String>) = ids.distinct().mapNotNull(factMap::get)
        val headlineEvidence = evidence(proposal.resumeHeadlineFactIds)
        val summaryEvidence = evidence(proposal.resumeSummaryFactIds)
        val sections = proposal.resumeSections.take(10).mapNotNull { section ->
            val sectionEvidence = evidence(section.verifiedFactIds)
            val heading = section.heading.trim().take(100)
            val content = section.content.trim().take(4000)
            if (heading.isBlank() || content.isBlank() || sectionEvidence.isEmpty()) null
            else CareerDraftSection(heading, content, sectionEvidence)
        }
        val projects = proposal.portfolioProjects.take(8).mapNotNull { project ->
            val projectEvidence = evidence(project.verifiedFactIds)
            val title = project.title.trim().take(200)
            val summary = project.summary.trim().take(3000)
            if (title.isBlank() || summary.isBlank() || projectEvidence.isEmpty()) null
            else CareerPortfolioProject(title, project.selectionReason.trim().take(1000), summary, projectEvidence)
        }
        return CareerPostingDraftResult(
            posting = posting,
            resumeHeadline = proposal.resumeHeadline.trim().take(500).takeIf { headlineEvidence.isNotEmpty() }.orEmpty(),
            resumeHeadlineEvidence = headlineEvidence,
            resumeSummary = proposal.resumeSummary.trim().take(3000).takeIf { summaryEvidence.isNotEmpty() }.orEmpty(),
            resumeSummaryEvidence = summaryEvidence,
            resumeSections = sections,
            portfolioProjects = projects,
            informationGaps = cleanLines(proposal.informationGaps, 12, 500),
        )
    }

    private fun postingFromNode(node: JsonNode): CareerRecommendationPosting? {
        if (node.isMissingNode || node.isNull) return null
        val id = node["postingId"]?.asLong() ?: node["id"]?.asLong() ?: return null
        val company = node["company"]?.asString().orEmpty()
        val title = node["title"]?.asString().orEmpty()
        if (company.isBlank() || title.isBlank()) return null
        val categories = node["categories"]?.takeIf { it.isArray }?.values()?.map { it.asString() }.orEmpty()
        val firstSeen = node["firstSeen"]?.asString()?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH
        return CareerRecommendationPosting(
            id = id,
            company = company.take(300),
            title = title.take(1000),
            location = node["location"]?.asString().orEmpty().take(500),
            url = node["url"]?.asString().orEmpty().take(2048),
            categories = categories,
            firstSeen = firstSeen,
        )
    }

    private fun factViews(node: JsonNode?): Map<String, CareerFactView> = node?.takeIf { it.isArray }
        ?.values()?.mapNotNull { fact ->
            val id = fact["id"]?.asString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val evidence = fact["evidence"]?.takeIf { it.isArray }?.values()?.mapNotNull { source ->
                val quote = source["excerpt"]?.asString()?.trim().orEmpty().take(1000)
                if (quote.isBlank()) null else CareerFactEvidenceView(source["locationLabel"]?.asString().orEmpty().take(255), quote)
            }.orEmpty()
            CareerFactView(id, fact["title"]?.asString().orEmpty().take(500), evidence)
        }?.associateBy { it.id }.orEmpty()

    private fun view(task: CareerAssistantTask): CareerAssistantTaskView {
        val request = runCatching { mapper.readTree(task.requestJson) }.getOrNull()
        return CareerAssistantTaskView(
            id = task.id,
            type = task.type,
            parentTaskId = task.parentTaskId,
            postingId = task.postingId,
            status = task.status,
            errorMessage = task.errorMessage,
            attempts = task.attempts,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
            result = task.resultJson?.let { runCatching { mapper.readTree(it) }.getOrNull() },
            verifiedFactCount = request?.get("verifiedFactCount")?.asInt() ?: 0,
            factsTruncated = request?.get("factsTruncated")?.asBoolean() ?: false,
            candidateCount = request?.get("candidateCount")?.asInt() ?: 1,
            candidatesTruncated = request?.get("candidatesTruncated")?.asBoolean() ?: false,
        )
    }

    private fun saveNewTask(type: CareerAssistantTaskType, requestJson: String): CareerAssistantTaskView {
        val now = Instant.now()
        return view(tasks.save(CareerAssistantTask(
            id = UUID.randomUUID().toString(),
            type = type,
            status = CareerAssistantTaskStatus.WAITING_AI,
            requestJson = requestJson,
            createdAt = now,
            updatedAt = now,
        )))
    }

    private fun factPrompt(fact: CareerFact) = mapOf(
        "id" to fact.id,
        "kind" to fact.kind.name,
        "title" to fact.title.take(500),
        "summary" to fact.summary.take(1600),
        "evidence" to fact.evidence.take(3).map { mapOf("locationLabel" to it.locationLabel, "excerpt" to it.excerpt.take(600)) },
    )

    private fun requireLease(task: CareerAssistantTask, token: String) {
        if (task.status != CareerAssistantTaskStatus.ANALYZING || task.leaseToken != token) throw LeaseLost()
    }

    private fun splitTerms(value: String) = value.split(',', '，', ';', '；').map(String::trim).filter(String::isNotBlank).distinct()

    private fun tokens(value: String): List<String> {
        val stop = setOf("경험", "업무", "담당", "개발", "기반", "서비스", "기술", "프로젝트", "관련", "통해", "위한", "그리고", "the", "and", "with", "for", "from", "your", "you")
        return Regex("[A-Za-z][A-Za-z0-9+#.]{1,}|[가-힣]{2,}")
            .findAll(value.lowercase())
            .map { it.value.trimEnd('.') }
            .filter { it.length >= 2 && it !in stop }
            .toList()
    }

    private fun cleanLines(values: List<String>, limit: Int, maxLength: Int) = values.asSequence()
        .map { it.trim().take(maxLength) }.filter(String::isNotBlank).distinct().take(limit).toList()

    private fun safeError(value: String) = value
        .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~-]+"), "Bearer [숨김]")
        .replace(Regex("sk-[A-Za-z0-9_-]{12,}"), "sk-[숨김]")
        .trim().take(1800)

    private data class RankedPosting(val posting: Posting, val body: String, val score: Int)

    companion object {
        private const val MAX_RECOMMENDATIONS = 20
        private const val MAX_FACTS = 80
        private const val MAX_CANDIDATES = 36
        private const val MAX_RECOMMENDATION_BODY_CHARS = 3600
        private const val MAX_DRAFT_BODY_CHARS = 18_000
        private const val MAX_RECENT_TASKS = 20
        private val TASK_LEASE = Duration.ofMinutes(10)
    }
}

data class CareerRecommendationRequest(
    val count: Int = 10,
    val categoryIds: Set<String> = emptySet(),
    val companyKeywords: String = "",
    val locationKeywords: String = "",
)

data class CareerAssistantReadiness(val verifiedFactCount: Int, val openPostingCount: Int)

data class CareerAssistantTaskView(
    val id: String,
    val type: CareerAssistantTaskType,
    val parentTaskId: String?,
    val postingId: Long?,
    val status: CareerAssistantTaskStatus,
    val errorMessage: String?,
    val attempts: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val result: JsonNode?,
    val verifiedFactCount: Int,
    val factsTruncated: Boolean,
    val candidateCount: Int,
    val candidatesTruncated: Boolean,
)
