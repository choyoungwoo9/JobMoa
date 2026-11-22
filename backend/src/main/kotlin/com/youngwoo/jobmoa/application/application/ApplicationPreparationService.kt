package com.youngwoo.jobmoa.application.application

import com.youngwoo.jobmoa.application.domain.*
import com.youngwoo.jobmoa.applicationrequirements.application.ApplicationRequirementService
import com.youngwoo.jobmoa.applicationrequirements.application.ApplicationRequirementView
import com.youngwoo.jobmoa.applicationrequirements.domain.RequirementSource
import com.youngwoo.jobmoa.career.application.CareerMaterialService
import com.youngwoo.jobmoa.career.domain.model.CareerFact
import com.youngwoo.jobmoa.career.domain.repository.CareerMaterialRepository
import com.youngwoo.jobmoa.career.assistant.domain.model.CareerPostingDraftProposal
import com.youngwoo.jobmoa.crawler.infrastructure.browser.PublicUrlGuard
import com.youngwoo.jobmoa.posting.domain.model.Posting
import com.youngwoo.jobmoa.posting.domain.repository.PostingRepository
import com.youngwoo.jobmoa.shared.LeaseLost
import com.youngwoo.jobmoa.shared.ResourceNotFound
import com.youngwoo.jobmoa.workspace.application.WorkspaceAccess
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class ApplicationPreparationService(
    private val repository: ApplicationPreparationRepository,
    private val tasks: ApplicationAiTaskRepository,
    private val postings: PostingRepository,
    private val requirements: ApplicationRequirementService,
    private val careerFacts: CareerMaterialRepository,
    private val careerMaterials: CareerMaterialService,
    private val access: WorkspaceAccess,
    private val mapper: JsonMapper,
) {
    @Transactional
    fun create(postingId: Long): ApplicationCaseSummary {
        access.writing()
        val posting = postings.lock(postingId) ?: throw ResourceNotFound("공고를 찾을 수 없습니다.")
        repository.findCaseByPosting(posting.id)?.let { return summary(it, posting) }
        val now = Instant.now()
        val value = ApplicationCase(UUID.randomUUID().toString(), posting.id, ApplicationCaseStatus.PREPARING, now, null, now, now)
        return summary(repository.saveCase(value), posting)
    }

    @Transactional(readOnly = true)
    fun list(): List<ApplicationCaseSummary> = repository.cases().mapNotNull { row -> postings.find(row.postingId)?.let { summary(row, it) } }

    @Transactional(readOnly = true)
    fun statuses(): List<ApplicationCaseStatusView> = repository.cases().map { ApplicationCaseStatusView(it.postingId, it.status, it.appliedAt) }

    @Transactional(readOnly = true)
    fun detail(id: String): ApplicationCaseDetail {
        val row = repository.findCase(id) ?: throw ResourceNotFound("지원 준비 건을 찾을 수 없습니다.")
        val posting = postings.find(row.postingId) ?: throw ResourceNotFound("공고를 찾을 수 없습니다.")
        val latestRequirements = requirements.list(row.postingId)
        val requirement = latestRequirements.firstOrNull { it.versionSaved && it.rawText.isNotBlank() && it.source != RequirementSource.POSTING_BODY }
        val newestRequirement = latestRequirements.firstOrNull { it.source != RequirementSource.POSTING_BODY }
        val research = tasks.latest(id, ApplicationAiTaskType.INTERVIEW_RESEARCH)
        val extract = tasks.latest(id, ApplicationAiTaskType.EXTRACT_QUESTIONS)
        val materialTask = tasks.latest(id, ApplicationAiTaskType.MATERIAL_DRAFT)
        val session = repository.sessions(id).firstOrNull()?.let { s -> ApplicationInterviewSessionView(s, repository.turns(s.id)) }
        return ApplicationCaseDetail(
            summary(row, posting),
            latestRequirements.firstOrNull { it.source == RequirementSource.PUBLIC_PAGE && !it.versionSaved } ?: newestRequirement,
            requirement,
            repository.questions(id).map(::questionView),
            repository.draft(id)?.let { mapper.readTree(it.draftJson) },
            repository.draft(id)?.version,
            taskView(materialTask),
            taskView(extract),
            taskView(research),
            session,
            tasks.recent(id, MAX_RECENT_TASKS).mapNotNull(::taskView),
        )
    }

    @Transactional
    fun updateStatus(id: String, status: ApplicationCaseStatus): ApplicationCaseSummary {
        access.writing()
        val current = repository.lockCase(id) ?: throw ResourceNotFound("지원 준비 건을 찾을 수 없습니다.")
        val now = Instant.now()
        val updated = current.copy(status = status, appliedAt = if (status == ApplicationCaseStatus.APPLIED) current.appliedAt ?: now else null, updatedAt = now)
        val posting = postings.find(current.postingId) ?: throw ResourceNotFound("공고를 찾을 수 없습니다.")
        return summary(repository.saveCase(updated), posting)
    }

    @Transactional
    fun addQuestion(id: String, request: SaveApplicationQuestionRequest): ApplicationQuestionView {
        access.writing()
        val app = requireCase(id)
        val prompt = request.prompt.trim()
        require(prompt.isNotBlank() && prompt.length <= MAX_QUESTION_CHARS) { "문항은 1~${MAX_QUESTION_CHARS}자까지 입력할 수 있습니다." }
        require(request.characterLimit == null || request.characterLimit in 1..MAX_ANSWER_CHARS) { "글자 수 제한은 1~${MAX_ANSWER_CHARS}자여야 합니다." }
        val now = Instant.now()
        val row = ApplicationQuestion(UUID.randomUUID().toString(), id, null, repository.questions(id).size, prompt,
            request.characterLimit, null, "", "[]", null, now, now)
        repository.saveQuestion(row)
        return questionView(row)
    }

    @Transactional
    fun updateQuestion(applicationId: String, questionId: String, request: SaveApplicationQuestionRequest): ApplicationQuestionView {
        access.writing()
        requireCase(applicationId)
        val current = repository.lockQuestion(questionId)?.takeIf { it.applicationId == applicationId }
            ?: throw ResourceNotFound("지원서 문항을 찾을 수 없습니다.")
        val prompt = request.prompt.trim()
        require(prompt.isNotBlank() && prompt.length <= MAX_QUESTION_CHARS) { "문항은 1~${MAX_QUESTION_CHARS}자까지 입력할 수 있습니다." }
        require(request.characterLimit == null || request.characterLimit in 1..MAX_ANSWER_CHARS) { "글자 수 제한은 1~${MAX_ANSWER_CHARS}자여야 합니다." }
        val answer = request.answer?.trimEnd() ?: current.answerDraft
        require(answer.length <= (request.characterLimit ?: MAX_ANSWER_CHARS)) { "답변이 설정된 글자 수 제한을 넘었습니다." }
        val updated = current.copy(prompt = prompt, characterLimit = request.characterLimit, answerDraft = answer,
            answerFactIdsJson = if (request.answer == null) current.answerFactIdsJson else "[]",
            answerTaskId = if (request.answer == null) current.answerTaskId else null, updatedAt = Instant.now())
        return questionView(repository.saveQuestion(updated))
    }

    @Transactional
    fun deleteQuestion(applicationId: String, questionId: String) {
        access.writing()
        requireCase(applicationId)
        val current = repository.lockQuestion(questionId)?.takeIf { it.applicationId == applicationId }
            ?: throw ResourceNotFound("지원서 문항을 찾을 수 없습니다.")
        repository.deleteQuestion(current.id)
    }

    @Transactional
    fun saveAnswer(applicationId: String, questionId: String, answer: String): ApplicationQuestionView {
        access.writing()
        requireCase(applicationId)
        val current = repository.lockQuestion(questionId)?.takeIf { it.applicationId == applicationId }
            ?: throw ResourceNotFound("지원서 문항을 찾을 수 없습니다.")
        val text = answer.trimEnd()
        require(text.length <= (current.characterLimit ?: MAX_ANSWER_CHARS)) { "답변이 설정된 글자 수 제한을 넘었습니다." }
        return questionView(repository.saveQuestion(current.copy(answerDraft = text, answerFactIdsJson = "[]", answerTaskId = null, updatedAt = Instant.now())))
    }

    @Transactional
    fun extractQuestions(applicationId: String): ApplicationAiTaskView {
        val app = requireCase(applicationId)
        activeTask(applicationId, ApplicationAiTaskType.EXTRACT_QUESTIONS)?.let { return taskView(it)!! }
        val requirement = requirements.list(app.postingId).firstOrNull { it.versionSaved && it.rawText.isNotBlank() && it.source != RequirementSource.POSTING_BODY }
            ?: throw IllegalArgumentException("지원서 원문이 없습니다. 공고 상세의 지원서 정보에 원문을 저장한 뒤 문항을 추출하세요.")
        val input = mapOf(
            "applicationId" to applicationId,
            "requirementId" to requirement.id,
            "sourceUrl" to requirement.sourceUrl,
            "rawText" to requirement.rawText.take(MAX_REQUIREMENT_CHARS),
        )
        return taskView(createTask(app, ApplicationAiTaskType.EXTRACT_QUESTIONS, input))!!
    }

    @Transactional
    fun draftAnswer(applicationId: String, questionId: String): ApplicationAiTaskView {
        val app = requireCase(applicationId)
        val question = repository.lockQuestion(questionId)?.takeIf { it.applicationId == applicationId }
            ?: throw ResourceNotFound("지원서 문항을 찾을 수 없습니다.")
        activeTask(applicationId, ApplicationAiTaskType.DRAFT_ANSWER)?.takeIf {
            runCatching { mapper.readTree(it.requestJson)["questionId"]?.asString() == questionId }.getOrDefault(false)
        }?.let { return taskView(it)!! }
        val posting = postings.find(app.postingId) ?: throw ResourceNotFound("공고를 찾을 수 없습니다.")
        val body = postings.latestVersion(app.postingId)?.bodyText.orEmpty()
        val facts = careerFacts.recommendationFacts().take(MAX_FACTS).map(::factPrompt)
        require(facts.isNotEmpty()) { "답변 근거로 사용할 경력 자료가 없습니다. 경력 자료를 먼저 등록해 주세요." }
        val input = mapOf(
            "applicationId" to applicationId,
            "questionId" to questionId,
            "question" to question.prompt,
            "characterLimit" to question.characterLimit,
            "posting" to postingContext(posting, body),
            "facts" to facts,
        )
        return taskView(createTask(app, ApplicationAiTaskType.DRAFT_ANSWER, input))!!
    }

    @Transactional
    fun generateMaterials(applicationId: String): ApplicationAiTaskView {
        val app = requireCase(applicationId)
        repository.draft(applicationId)?.let { throw IllegalArgumentException("이 공고의 맞춤 자료 초안이 이미 있습니다. 저장된 초안을 편집하세요.") }
        activeTask(applicationId, ApplicationAiTaskType.MATERIAL_DRAFT)?.let { return taskView(it)!! }
        val posting = postings.find(app.postingId) ?: throw ResourceNotFound("공고를 찾을 수 없습니다.")
        val body = postings.latestVersion(app.postingId)?.bodyText.orEmpty()
        val facts = careerFacts.recommendationFacts().take(MAX_FACTS).map(::factPrompt)
        require(facts.isNotEmpty()) { "맞춤 자료에 사용할 경력 정보가 없습니다. 경력 자료를 먼저 등록해 주세요." }
        val input = mapOf("applicationId" to applicationId, "posting" to postingContext(posting, body), "facts" to facts)
        return taskView(createTask(app, ApplicationAiTaskType.MATERIAL_DRAFT, input))!!
    }

    @Transactional
    fun saveMaterials(applicationId: String, request: SaveApplicationMaterialsRequest): ApplicationMaterialsDraftView {
        access.writing()
        requireCase(applicationId)
        val current = repository.draft(applicationId) ?: throw ResourceNotFound("생성된 맞춤 자료가 없습니다.")
        val base = mapper.readTree(current.draftJson)
        val sections = base["resumeSections"]?.takeIf { it.isArray }?.associateBy { it["id"].asString() }.orEmpty()
        val projects = base["portfolioProjects"]?.takeIf { it.isArray }?.associateBy { it["id"].asString() }.orEmpty()
        require(request.resumeHeadline.length <= 500 && request.resumeSummary.length <= 3000) { "이력서 소개·요약이 허용 길이를 넘었습니다." }
        require(request.resumeSections.size <= 10 && request.portfolioProjects.size <= 8) { "이력서 또는 포트폴리오 항목이 너무 많습니다." }
        val updatedSections = request.resumeSections.map { edit ->
            val source = sections[edit.id] ?: throw IllegalArgumentException("초안에 없는 이력서 항목입니다.")
            require(edit.heading.isNotBlank() && edit.heading.length <= 100 && edit.content.isNotBlank() && edit.content.length <= 4000) { "이력서 항목은 제목 100자, 내용 4,000자까지 입력하세요." }
            mapper.createObjectNode().apply {
                put("id", edit.id); put("heading", edit.heading.trim()); put("content", edit.content.trim())
                put("included", edit.included); put("order", edit.order.coerceIn(0, 99))
                set("factIds", source["factIds"]); set("evidence", source["evidence"])
            }
        }.sortedBy { it["order"].asInt() }
        val updatedProjects = request.portfolioProjects.map { edit ->
            val source = projects[edit.id] ?: throw IllegalArgumentException("초안에 없는 포트폴리오 항목입니다.")
            require(edit.title.isNotBlank() && edit.title.length <= 200 && edit.summary.isNotBlank() && edit.summary.length <= 3000) { "프로젝트 제목은 200자, 요약은 3,000자까지 입력하세요." }
            mapper.createObjectNode().apply {
                put("id", edit.id); put("title", edit.title.trim()); put("selectionReason", edit.selectionReason.trim().take(1000))
                put("summary", edit.summary.trim()); put("included", edit.included); put("order", edit.order.coerceIn(0, 99))
                set("factIds", source["factIds"]); set("evidence", source["evidence"])
            }
        }.sortedBy { it["order"].asInt() }
        val saved = (base.deepCopy() as ObjectNode).apply {
            put("resumeHeadline", request.resumeHeadline.trim()); put("resumeSummary", request.resumeSummary.trim())
            set("resumeSections", mapper.valueToTree(updatedSections)); set("portfolioProjects", mapper.valueToTree(updatedProjects))
            put("editedByUser", true)
        }
        val now = Instant.now()
        val updated = repository.saveDraft(current.copy(draftJson = mapper.writeValueAsString(saved), version = current.version + 1, updatedAt = now))
        return ApplicationMaterialsDraftView(mapper.readTree(updated.draftJson), updated.version, updated.updatedAt)
    }

    @Transactional
    fun researchInterview(applicationId: String): ApplicationAiTaskView {
        val app = requireCase(applicationId)
        activeTask(applicationId, ApplicationAiTaskType.INTERVIEW_RESEARCH)?.let { return taskView(it)!! }
        val posting = postings.find(app.postingId) ?: throw ResourceNotFound("공고를 찾을 수 없습니다.")
        val body = postings.latestVersion(app.postingId)?.bodyText.orEmpty()
        val facts = careerFacts.recommendationFacts().take(MAX_FACTS).map(::factPrompt)
        val answers = repository.questions(applicationId).filter { it.answerDraft.isNotBlank() }.take(20).map {
            mapOf("question" to it.prompt, "answer" to it.answerDraft.take(2000))
        }
        val input = mapOf(
            "applicationId" to applicationId,
            "posting" to postingContext(posting, body),
            "facts" to facts,
            "materials" to repository.draft(applicationId)?.draftJson?.let(mapper::readTree),
            "applicationAnswers" to answers,
        )
        return taskView(createTask(app, ApplicationAiTaskType.INTERVIEW_RESEARCH, input))!!
    }

    @Transactional
    fun retryTask(applicationId: String, taskId: String): ApplicationAiTaskView {
        access.writing()
        requireCase(applicationId)
        val task = tasks.lock(taskId)?.takeIf { it.applicationId == applicationId } ?: throw ResourceNotFound("AI 작업을 찾을 수 없습니다.")
        require(task.status == ApplicationAiTaskStatus.FAILED) { "실패한 작업만 다시 시도할 수 있습니다." }
        val saved = tasks.save(task.copy(status = ApplicationAiTaskStatus.WAITING_AI, resultJson = null, errorMessage = null,
            progressMessage = null, leaseToken = null, leaseExpiresAt = null, updatedAt = Instant.now()))
        return taskView(saved)!!
    }

    @Transactional
    fun startInterview(applicationId: String): ApplicationInterviewSessionView {
        access.writing()
        requireCase(applicationId)
        repository.sessions(applicationId).firstOrNull { it.status == ApplicationInterviewStatus.ACTIVE }?.let { active ->
            return ApplicationInterviewSessionView(active, repository.turns(active.id))
        }
        val research = tasks.latest(applicationId, ApplicationAiTaskType.INTERVIEW_RESEARCH)
            ?.takeIf { it.status == ApplicationAiTaskStatus.COMPLETED }?.resultJson?.let(mapper::readTree)
            ?: throw IllegalArgumentException("먼저 면접 경향 조사를 완료하세요.")
        val questions = research["questions"]?.takeIf { it.isArray }?.values()?.toList().orEmpty()
        require(questions.isNotEmpty()) { "면접 질문을 만들지 못했습니다. 조사를 다시 실행하세요." }
        val now = Instant.now()
        val session = repository.saveSession(ApplicationInterviewSession(UUID.randomUUID().toString(), applicationId, ApplicationInterviewStatus.ACTIVE, 0, now, now))
        val first = questions.first()
        repository.saveTurn(ApplicationInterviewTurn(UUID.randomUUID().toString(), session.id, 0, first["basis"].asString(), first["question"].asString(),
            "", null, "", null, null, null, null, now, now))
        return ApplicationInterviewSessionView(session, repository.turns(session.id))
    }

    @Transactional
    fun answerInterview(applicationId: String, sessionId: String, turnId: String, answer: String): ApplicationAiTaskView {
        access.writing()
        val app = requireCase(applicationId)
        val session = repository.lockSession(sessionId)?.takeIf { it.applicationId == applicationId }
            ?: throw ResourceNotFound("경험 인터뷰를 찾을 수 없습니다.")
        require(session.status == ApplicationInterviewStatus.ACTIVE) { "완료된 경험 인터뷰입니다." }
        val turn = repository.lockTurn(turnId)?.takeIf { it.sessionId == sessionId }
            ?: throw ResourceNotFound("면접 질문을 찾을 수 없습니다.")
        val text = answer.trim()
        require(text.length in 10..MAX_INTERVIEW_ANSWER_CHARS) { "답변은 10~${MAX_INTERVIEW_ANSWER_CHARS}자로 입력하세요." }
        if (turn.followUpQuestion != null) throw IllegalArgumentException("꼬리 질문 답변을 먼저 마무리하세요.")
        val updated = repository.saveTurn(turn.copy(answer = text, updatedAt = Instant.now()))
        activeTask(applicationId, ApplicationAiTaskType.INTERVIEW_FOLLOW_UP)?.takeIf {
            runCatching { mapper.readTree(it.requestJson)["turnId"]?.asString() == turnId }.getOrDefault(false)
        }?.let { return taskView(it)!! }
        val posting = postings.find(app.postingId) ?: throw ResourceNotFound("공고를 찾을 수 없습니다.")
        val facts = careerFacts.recommendationFacts().take(MAX_FACTS).map(::factPrompt)
        val input = mapOf("applicationId" to applicationId, "sessionId" to sessionId, "turnId" to turnId,
            "basis" to turn.basis, "question" to turn.question, "answer" to updated.answer,
            "posting" to postingContext(posting, postings.latestVersion(app.postingId)?.bodyText.orEmpty()), "facts" to facts)
        return taskView(createTask(app, ApplicationAiTaskType.INTERVIEW_FOLLOW_UP, input))!!
    }

    @Transactional
    fun answerFollowUp(applicationId: String, sessionId: String, turnId: String, answer: String): ApplicationInterviewSessionView {
        access.writing()
        requireCase(applicationId)
        val turn = repository.lockTurn(turnId)?.takeIf { it.sessionId == sessionId }
            ?: throw ResourceNotFound("꼬리 질문을 찾을 수 없습니다.")
        require(turn.followUpQuestion != null) { "아직 꼬리 질문이 없습니다." }
        val text = answer.trim()
        require(text.length in 10..MAX_INTERVIEW_ANSWER_CHARS) { "답변은 10~${MAX_INTERVIEW_ANSWER_CHARS}자로 입력하세요." }
        repository.saveTurn(turn.copy(followUpAnswer = text, updatedAt = Instant.now()))
        val session = repository.lockSession(sessionId)?.takeIf { it.applicationId == applicationId }
            ?: throw ResourceNotFound("경험 인터뷰를 찾을 수 없습니다.")
        return ApplicationInterviewSessionView(session, repository.turns(sessionId))
    }

    @Transactional
    fun nextInterviewQuestion(applicationId: String, sessionId: String): ApplicationInterviewSessionView {
        access.writing()
        requireCase(applicationId)
        val session = repository.lockSession(sessionId)?.takeIf { it.applicationId == applicationId }
            ?: throw ResourceNotFound("경험 인터뷰를 찾을 수 없습니다.")
        require(session.status == ApplicationInterviewStatus.ACTIVE) { "완료된 경험 인터뷰입니다." }
        val turns = repository.turns(session.id)
        val last = turns.lastOrNull() ?: throw IllegalArgumentException("진행 중인 질문이 없습니다.")
        require(last.answer.isNotBlank() && last.followUpQuestion != null && last.followUpAnswer.isNotBlank()) { "현재 질문과 꼬리 질문에 모두 답한 뒤 다음으로 넘어가세요." }
        val research = tasks.latest(applicationId, ApplicationAiTaskType.INTERVIEW_RESEARCH)?.resultJson?.let(mapper::readTree)
            ?: throw ResourceNotFound("면접 조사 결과를 찾을 수 없습니다.")
        val questions = research["questions"]?.takeIf { it.isArray }?.values()?.toList().orEmpty()
        val nextIndex = session.nextQuestionIndex + 1
        if (nextIndex >= questions.size) {
            val completed = repository.saveSession(session.copy(status = ApplicationInterviewStatus.COMPLETED, nextQuestionIndex = nextIndex, updatedAt = Instant.now()))
            return ApplicationInterviewSessionView(completed, turns)
        }
        val next = questions[nextIndex]
        val now = Instant.now()
        repository.saveTurn(ApplicationInterviewTurn(UUID.randomUUID().toString(), sessionId, turns.size, next["basis"].asString(), next["question"].asString(),
            "", null, "", null, null, null, null, now, now))
        val updated = repository.saveSession(session.copy(nextQuestionIndex = nextIndex, updatedAt = now))
        return ApplicationInterviewSessionView(updated, repository.turns(sessionId))
    }

    @Transactional
    fun promoteInterviewExperience(applicationId: String, sessionId: String, turnId: String, request: PromoteInterviewExperienceRequest): Map<String, Any> {
        access.writing()
        requireCase(applicationId)
        val turn = repository.lockTurn(turnId)?.takeIf { it.sessionId == sessionId }
            ?: throw ResourceNotFound("경험 인터뷰 답변을 찾을 수 없습니다.")
        require(turn.promotedMaterialId == null) { "이미 경력 자료에 반영한 경험입니다." }
        val title = request.title.trim()
        val summary = request.summary.trim()
        require(title.isNotBlank() && title.length <= 500 && summary.isNotBlank() && summary.length <= 8000) { "제목은 500자, 경험 설명은 8,000자까지 입력하세요." }
        val evidence = turn.sourceExcerpt?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("답변에서 확인된 근거 문장이 없어 경력 자료에 반영할 수 없습니다.")
        val fact = careerMaterials.saveInterviewFact(title, summary, evidence)
        repository.saveTurn(turn.copy(promotedMaterialId = fact.materialId, updatedAt = Instant.now()))
        return mapOf("materialId" to fact.materialId, "factId" to fact.id, "title" to fact.title, "summary" to fact.summary)
    }

    @Transactional
    fun claimTask(): ApplicationAiTaskLease? {
        access.writing()
        return tasks.claimNext(Instant.now(), TASK_LEASE)?.let { ApplicationAiTaskLease(it, requireNotNull(it.leaseToken)) }
    }

    @Transactional
    fun renewTask(id: String, token: String): Boolean {
        access.writing()
        return tasks.renew(id, token, Instant.now(), TASK_LEASE)
    }

    @Transactional
    fun progressTask(id: String, token: String, message: String) {
        access.writing()
        val task = tasks.lock(id) ?: throw ResourceNotFound("AI 작업을 찾을 수 없습니다.")
        requireLease(task, token)
        tasks.save(task.copy(progressMessage = message.trim().take(255), updatedAt = Instant.now()))
    }

    @Transactional
    fun completeTask(id: String, token: String, raw: JsonNode) {
        access.writing()
        val task = tasks.lock(id) ?: throw ResourceNotFound("AI 작업을 찾을 수 없습니다.")
        requireLease(task, token)
        val request = mapper.readTree(task.requestJson)
        val normalized = when (task.type) {
            ApplicationAiTaskType.MATERIAL_DRAFT -> normalizeMaterials(task, request, raw)
            ApplicationAiTaskType.EXTRACT_QUESTIONS -> normalizeQuestions(task, request, raw)
            ApplicationAiTaskType.DRAFT_ANSWER -> normalizeAnswer(task, request, raw)
            ApplicationAiTaskType.INTERVIEW_RESEARCH -> normalizeResearch(raw)
            ApplicationAiTaskType.INTERVIEW_FOLLOW_UP -> normalizeFollowUp(task, request, raw)
        }
        tasks.save(task.copy(status = ApplicationAiTaskStatus.COMPLETED, resultJson = mapper.writeValueAsString(normalized),
            errorMessage = null, progressMessage = null, leaseToken = null, leaseExpiresAt = null, updatedAt = Instant.now()))
    }

    @Transactional
    fun failTask(id: String, token: String, message: String) {
        access.writing()
        val task = tasks.lock(id) ?: throw ResourceNotFound("AI 작업을 찾을 수 없습니다.")
        requireLease(task, token)
        tasks.save(task.copy(status = ApplicationAiTaskStatus.FAILED, errorMessage = safeError(message), progressMessage = null,
            leaseToken = null, leaseExpiresAt = null, updatedAt = Instant.now()))
    }

    @Transactional(readOnly = true)
    fun task(applicationId: String, taskId: String): ApplicationAiTaskView {
        requireCase(applicationId)
        val task = tasks.find(taskId)?.takeIf { it.applicationId == applicationId } ?: throw ResourceNotFound("AI 작업을 찾을 수 없습니다.")
        return taskView(task)!!
    }

    private fun normalizeMaterials(task: ApplicationAiTask, request: JsonNode, raw: JsonNode): JsonNode {
        val proposal = mapper.treeToValue(raw, CareerPostingDraftProposal::class.java)
        val factMap = factContextMap(request["facts"])
        fun evidence(ids: List<String>) = ids.distinct().mapNotNull(factMap::get)
        val headingFactIds = proposal.resumeHeadlineFactIds.filter { it in factMap }
        val summaryFactIds = proposal.resumeSummaryFactIds.filter { it in factMap }
        val result = mapper.createObjectNode()
        result.put("sourceTaskId", task.id)
        result.set("posting", request["posting"])
        result.put("resumeHeadline", proposal.resumeHeadline.trim().take(500).takeIf { headingFactIds.isNotEmpty() }.orEmpty())
        result.put("resumeSummary", proposal.resumeSummary.trim().take(3000).takeIf { summaryFactIds.isNotEmpty() }.orEmpty())
        result.set("resumeHeadlineFactIds", mapper.valueToTree(headingFactIds))
        result.set("resumeSummaryFactIds", mapper.valueToTree(summaryFactIds))
        result.set("resumeHeadlineEvidence", mapper.valueToTree(evidence(headingFactIds)))
        result.set("resumeSummaryEvidence", mapper.valueToTree(evidence(summaryFactIds)))
        val sections = proposal.resumeSections.take(10).mapIndexedNotNull { index, item ->
            val ids = item.verifiedFactIds.distinct().filter { it in factMap }
            if (ids.isEmpty() || item.heading.isBlank() || item.content.isBlank()) return@mapIndexedNotNull null
            mapper.createObjectNode().apply {
                put("id", "section-${index + 1}"); put("heading", item.heading.trim().take(100)); put("content", item.content.trim().take(4000))
                put("included", true); put("order", index); set("factIds", mapper.valueToTree(ids)); set("evidence", mapper.valueToTree(evidence(ids)))
            }
        }
        val projects = proposal.portfolioProjects.take(8).mapIndexedNotNull { index, item ->
            val ids = item.verifiedFactIds.distinct().filter { it in factMap }
            if (ids.isEmpty() || item.title.isBlank() || item.summary.isBlank()) return@mapIndexedNotNull null
            mapper.createObjectNode().apply {
                put("id", "project-${index + 1}"); put("title", item.title.trim().take(200)); put("selectionReason", item.selectionReason.trim().take(1000))
                put("summary", item.summary.trim().take(3000)); put("included", true); put("order", index)
                set("factIds", mapper.valueToTree(ids)); set("evidence", mapper.valueToTree(evidence(ids)))
            }
        }
        require(result["resumeHeadline"].asString().isNotBlank() || result["resumeSummary"].asString().isNotBlank() || sections.isNotEmpty()) { "근거가 연결된 이력서 초안을 만들지 못했습니다." }
        result.set("resumeSections", mapper.valueToTree(sections)); result.set("portfolioProjects", mapper.valueToTree(projects))
        result.set("informationGaps", mapper.valueToTree(cleanLines(raw["informationGaps"], 12, 500)))
        val now = Instant.now()
        repository.saveDraft(ApplicationMaterialsDraft(task.applicationId, task.id, mapper.writeValueAsString(result), 1, now, now))
        return result
    }

    private fun normalizeQuestions(task: ApplicationAiTask, request: JsonNode, raw: JsonNode): JsonNode {
        val requirementId = request["requirementId"].asString()
        val source = request["rawText"].asString()
        val current = repository.questions(task.applicationId).toMutableList()
        val newPrompts = mutableListOf<String>()
        raw["questions"]?.takeIf { it.isArray }?.values()?.take(MAX_QUESTIONS)?.forEach { candidate ->
            val prompt = candidate["prompt"]?.asString()?.trim().orEmpty().take(MAX_QUESTION_CHARS)
            val quote = candidate["sourceQuote"]?.asString()?.trim().orEmpty().take(2000)
            if (prompt.isBlank() || quote.isBlank() || !containsNormalized(source, quote)) return@forEach
            if (current.any { normalizeText(it.prompt) == normalizeText(prompt) }) return@forEach
            val proposedLimit = candidate["characterLimit"]?.takeIf { it.isIntegralNumber }?.asInt()
            val now = Instant.now()
            val row = ApplicationQuestion(UUID.randomUUID().toString(), task.applicationId, requirementId, current.size, prompt,
                proposedLimit?.takeIf { it in 1..MAX_ANSWER_CHARS }, quote, "", "[]", null, now, now)
            current.add(repository.saveQuestion(row)); newPrompts.add(prompt)
        }
        require(newPrompts.isNotEmpty()) { "원문에서 출처 문장을 확인할 수 있는 지원서 문항을 찾지 못했습니다." }
        return mapper.createObjectNode().apply {
            put("requirementId", requirementId); put("questionCount", newPrompts.size); set("questions", mapper.valueToTree(newPrompts))
        }
    }

    private fun normalizeAnswer(task: ApplicationAiTask, request: JsonNode, raw: JsonNode): JsonNode {
        val questionId = request["questionId"].asString()
        val question = repository.lockQuestion(questionId)?.takeIf { it.applicationId == task.applicationId }
            ?: throw ResourceNotFound("지원서 문항을 찾을 수 없습니다.")
        val answer = raw["answer"]?.asString()?.trim().orEmpty()
        require(answer.isNotBlank()) { "답변 초안이 비어 있습니다." }
        val max = question.characterLimit ?: MAX_ANSWER_CHARS
        require(answer.length <= max) { "답변 초안이 설정된 글자 수 제한을 넘었습니다. AI 초안을 다시 생성하거나 직접 줄여 주세요." }
        val safeAnswer = answer
        val facts = factContextMap(request["facts"])
        val factIds = raw["factIds"]?.takeIf { it.isArray }?.values()?.map { it.asString() }?.distinct()?.filter { it in facts }.orEmpty()
        require(factIds.isNotEmpty()) { "경력 근거가 연결되지 않은 답변 초안은 저장할 수 없습니다." }
        val saved = repository.saveQuestion(question.copy(answerDraft = safeAnswer, answerFactIdsJson = mapper.writeValueAsString(factIds), answerTaskId = task.id, updatedAt = Instant.now()))
        return mapper.createObjectNode().apply {
            put("questionId", saved.id); put("answer", safeAnswer); set("factIds", mapper.valueToTree(factIds))
            set("evidence", mapper.valueToTree(factIds.mapNotNull(facts::get)))
        }
    }

    private fun normalizeResearch(raw: JsonNode): JsonNode {
        val official = safeSources(raw["officialFacts"], requireOfficial = true)
        val individuals = safeSources(raw["individualReviews"], requireOfficial = false)
        val patterns = raw["reviewPatterns"]?.takeIf { it.isArray }?.values()?.mapNotNull { item ->
            val sources = safeSourceList(item["sources"])
            val independent = sources.distinctBy { it["url"].asString() }
            if (independent.size < 2) null else mapper.createObjectNode().apply {
                put("pattern", item["pattern"]?.asString().orEmpty().trim().take(300).ifBlank { return@mapNotNull null })
                put("summary", item["summary"]?.asString().orEmpty().trim().take(1500))
                put("confidence", confidence(item["confidence"]?.asString()))
                put("sourceCount", independent.size); set("sources", mapper.valueToTree(independent))
            }
        }.orEmpty().take(20)
        val inferences = raw["inferredTopics"]?.takeIf { it.isArray }?.values()?.mapNotNull { item ->
            val topic = item["topic"]?.asString()?.trim().orEmpty()
            val reason = item["reason"]?.asString()?.trim().orEmpty()
            if (topic.isBlank() || reason.isBlank()) null else mapOf("topic" to topic.take(300), "reason" to reason.take(1000))
        }.orEmpty().take(20)
        val reviewSourceUrls = (individuals.map { it["url"].asString() } + patterns.flatMap { pattern -> pattern["sources"].values().map { it["url"].asString() } }).toSet()
        val validQuestionBases = setOf("POSTING", "RESUME", "PORTFOLIO", "APPLICATION_ANSWER", "COMPANY_TREND")
        val questions = raw["questions"]?.takeIf { it.isArray }?.values()?.mapNotNull { item ->
            val basis = item["basis"]?.asString()?.uppercase().orEmpty()
            val question = item["question"]?.asString()?.trim().orEmpty()
            val reason = item["reason"]?.asString()?.trim().orEmpty()
            val sourceUrls = item["sourceUrls"]?.takeIf { it.isArray }?.values()?.map { it.asString() }?.filter { urlValid(it) }?.distinct().orEmpty().take(5)
            if (basis !in validQuestionBases || question.isBlank() || reason.isBlank()) null
            else if (basis == "COMPANY_TREND" && (sourceUrls.size < 2 || sourceUrls.any { it !in reviewSourceUrls })) null
            else mapOf("basis" to basis, "question" to question.take(1000), "reason" to reason.take(1000),
                "sourceUrls" to sourceUrls)
        }.orEmpty().take(40)
        require(official.isNotEmpty() || individuals.isNotEmpty() || inferences.isNotEmpty() || questions.isNotEmpty()) { "확인 가능한 면접 자료를 구성하지 못했습니다." }
        return mapper.createObjectNode().apply {
            put("searchedAt", Instant.now().toString()); put("searchPerformed", raw["searchPerformed"]?.asBoolean() ?: false)
            set("officialFacts", mapper.valueToTree(official)); set("reviewPatterns", mapper.valueToTree(patterns))
            set("individualReviews", mapper.valueToTree(individuals)); set("inferredTopics", mapper.valueToTree(inferences))
            set("questions", mapper.valueToTree(questions))
        }
    }

    private fun normalizeFollowUp(task: ApplicationAiTask, request: JsonNode, raw: JsonNode): JsonNode {
        val turn = repository.lockTurn(request["turnId"].asString())?.takeIf { it.sessionId == request["sessionId"].asString() }
            ?: throw ResourceNotFound("경험 인터뷰 답변을 찾을 수 없습니다.")
        val followUp = raw["followUpQuestion"]?.asString()?.trim().orEmpty().take(1000)
        require(followUp.isNotBlank()) { "꼬리 질문을 만들지 못했습니다." }
        val answer = request["answer"].asString()
        val quote = raw["sourceExcerpt"]?.asString()?.trim().orEmpty().take(1000)
        val safeQuote = quote.takeIf { it.isNotBlank() && containsNormalized(answer, it) }
        val title = raw["proposedTitle"]?.asString()?.trim()?.take(500)?.takeIf(String::isNotBlank)
        val summary = raw["proposedSummary"]?.asString()?.trim()?.take(3000)?.takeIf(String::isNotBlank)
        val updated = repository.saveTurn(turn.copy(followUpQuestion = followUp, proposedTitle = title, proposedSummary = summary,
            sourceExcerpt = safeQuote, updatedAt = Instant.now()))
        return mapper.createObjectNode().apply {
            put("turnId", updated.id); put("followUpQuestion", followUp)
            title?.let { put("proposedTitle", it) }; summary?.let { put("proposedSummary", it) }; safeQuote?.let { put("sourceExcerpt", it) }
        }
    }

    private fun safeSources(node: JsonNode?, requireOfficial: Boolean): List<JsonNode> = node?.takeIf { it.isArray }?.values()?.mapNotNull { item ->
        val title = item["title"]?.asString()?.trim().orEmpty()
        val url = item["url"]?.asString()?.trim().orEmpty()
        val statement = item["statement"]?.asString()?.trim().orEmpty()
        if (title.isBlank() || statement.isBlank() || !urlValid(url)) return@mapNotNull null
        if (requireOfficial && item["sourceKind"]?.asString() !in setOf("OFFICIAL", "COMPANY_BLOG")) return@mapNotNull null
        mapper.createObjectNode().apply {
            put("title", title.take(500)); put("url", url.take(2048)); put("statement", statement.take(1500))
            put("sourceKind", if (requireOfficial) "OFFICIAL" else "CANDIDATE_REVIEW")
            item["publishedAt"]?.asString()?.takeIf(String::isNotBlank)?.let { put("publishedAt", it.take(80)) }
            put("confidence", confidence(item["confidence"]?.asString()))
        }
    }.orEmpty().take(30)

    private fun safeSourceList(node: JsonNode?): List<JsonNode> = node?.takeIf { it.isArray }?.values()?.mapNotNull { item ->
        val title = item["title"]?.asString()?.trim().orEmpty()
        val url = item["url"]?.asString()?.trim().orEmpty()
        if (title.isBlank() || !urlValid(url)) null else mapper.createObjectNode().apply {
            put("title", title.take(500)); put("url", url.take(2048)); put("reason", (item["reason"]?.asString() ?: item["statement"]?.asString()).orEmpty().trim().take(500))
            item["publishedAt"]?.asString()?.takeIf(String::isNotBlank)?.let { put("publishedAt", it.take(80)) }
        }
    }.orEmpty().take(10)

    private fun urlValid(value: String): Boolean = runCatching { PublicUrlGuard.validate(value); true }.getOrDefault(false)
    private fun confidence(value: String?) = value?.uppercase()?.takeIf { it in setOf("HIGH", "MEDIUM", "LOW") } ?: "LOW"
    private fun factContextMap(node: JsonNode?): Map<String, JsonNode> = node?.takeIf { it.isArray }?.values()?.mapNotNull { item ->
        item["id"]?.asString()?.takeIf(String::isNotBlank)?.let { it to item }
    }?.toMap().orEmpty()
    private fun factPrompt(fact: CareerFact) = mapOf("id" to fact.id, "kind" to fact.kind.name, "title" to fact.title.take(500), "summary" to fact.summary.take(1400),
        "evidence" to fact.evidence.take(3).map { mapOf("locationLabel" to it.locationLabel, "excerpt" to it.excerpt.take(600)) })
    private fun postingContext(posting: Posting, body: String) = mapOf("postingId" to posting.id, "company" to posting.company, "title" to posting.title,
        "location" to posting.location, "url" to posting.url, "categories" to posting.categories.sorted(), "closed" to posting.closed, "body" to body.take(MAX_POSTING_BODY_CHARS))
    private fun taskView(task: ApplicationAiTask?): ApplicationAiTaskView? = task?.let {
        ApplicationAiTaskView(it.id, it.applicationId, it.type, it.status, it.errorMessage, it.progressMessage, it.attempts, it.createdAt, it.updatedAt,
            it.resultJson?.let { json -> runCatching { mapper.readTree(json) }.getOrNull() })
    }
    private fun questionView(row: ApplicationQuestion): ApplicationQuestionView {
        val ids = runCatching { mapper.readTree(row.answerFactIdsJson).values().map { it.asString() } }.getOrDefault(emptyList())
        return ApplicationQuestionView(row.id, row.position, row.prompt, row.characterLimit, row.sourceRequirementId, row.sourceQuote, row.answerDraft, ids, row.updatedAt)
    }
    private fun summary(row: ApplicationCase, posting: Posting) = ApplicationCaseSummary(
        row.id, posting.id, posting.sourceId, posting.company, posting.title, posting.location, posting.url, posting.firstSeen, posting.closed,
        row.status, row.startedAt, row.appliedAt, row.updatedAt)
    private fun activeTask(applicationId: String, type: ApplicationAiTaskType) = tasks.latest(applicationId, type)?.takeIf {
        it.status in setOf(ApplicationAiTaskStatus.WAITING_AI, ApplicationAiTaskStatus.ANALYZING)
    }
    private fun createTask(app: ApplicationCase, type: ApplicationAiTaskType, input: Any): ApplicationAiTask {
        access.writing()
        val now = Instant.now()
        val initialProgress = when (type) {
            ApplicationAiTaskType.MATERIAL_DRAFT -> "맞춤 이력서와 포트폴리오를 구성할 준비 중"
            ApplicationAiTaskType.EXTRACT_QUESTIONS -> "지원서 문항을 확인할 준비 중"
            ApplicationAiTaskType.DRAFT_ANSWER -> "경력 근거를 바탕으로 답변을 준비 중"
            ApplicationAiTaskType.INTERVIEW_RESEARCH -> "회사·직무 공개 정보를 검색할 준비 중"
            ApplicationAiTaskType.INTERVIEW_FOLLOW_UP -> "답변에 맞는 꼬리 질문을 준비 중"
        }
        return tasks.save(ApplicationAiTask(UUID.randomUUID().toString(), app.id, app.postingId, type, ApplicationAiTaskStatus.WAITING_AI,
            mapper.writeValueAsString(input), null, null, initialProgress, 0, null, null, now, now))
    }
    private fun requireCase(id: String) = repository.findCase(id) ?: throw ResourceNotFound("지원 준비 건을 찾을 수 없습니다.")
    private fun requireLease(task: ApplicationAiTask, token: String) {
        if (task.status != ApplicationAiTaskStatus.ANALYZING || task.leaseToken != token || task.leaseExpiresAt?.isAfter(Instant.now()) != true) throw LeaseLost()
    }
    private fun containsNormalized(source: String, quote: String) = normalizeText(source).contains(normalizeText(quote))
    private fun normalizeText(value: String) = value.replace(Regex("\\s+"), " ").trim().lowercase()
    private fun cleanLines(node: JsonNode?, limit: Int, maxLength: Int) = node?.takeIf { it.isArray }?.values()?.map { it.asString().trim().take(maxLength) }
        ?.filter(String::isNotBlank)?.distinct()?.take(limit).orEmpty()
    private fun safeError(value: String) = value.replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~-]+"), "Bearer [숨김]")
        .replace(Regex("sk-[A-Za-z0-9_-]{12,}"), "sk-[숨김]").trim().take(1800)

    companion object {
        private const val MAX_FACTS = 80
        private const val MAX_QUESTIONS = 40
        private const val MAX_QUESTION_CHARS = 8000
        private const val MAX_ANSWER_CHARS = 12_000
        private const val MAX_REQUIREMENT_CHARS = 100_000
        private const val MAX_POSTING_BODY_CHARS = 18_000
        private const val MAX_INTERVIEW_ANSWER_CHARS = 12_000
        private const val MAX_RECENT_TASKS = 20
        private val TASK_LEASE = Duration.ofMinutes(10)
    }
}

data class ApplicationCaseSummary(
    val id: String, val postingId: Long, val sourceId: String, val company: String, val title: String, val location: String, val url: String,
    val firstSeen: Instant, val closed: Boolean, val status: ApplicationCaseStatus, val startedAt: Instant, val appliedAt: Instant?, val updatedAt: Instant,
)
data class ApplicationCaseStatusView(val postingId: Long, val status: ApplicationCaseStatus, val appliedAt: Instant?)
data class ApplicationCaseDetail(
    val application: ApplicationCaseSummary,
    val latestRequirementActivity: ApplicationRequirementView?,
    val requirement: ApplicationRequirementView?,
    val questions: List<ApplicationQuestionView>,
    val materials: JsonNode?,
    val materialsVersion: Int?,
    val materialsTask: ApplicationAiTaskView?,
    val questionExtractionTask: ApplicationAiTaskView?,
    val interviewResearchTask: ApplicationAiTaskView?,
    val interviewSession: ApplicationInterviewSessionView?,
    val recentTasks: List<ApplicationAiTaskView>,
)
data class ApplicationQuestionView(
    val id: String, val position: Int, val prompt: String, val characterLimit: Int?, val sourceRequirementId: String?, val sourceQuote: String?,
    val answerDraft: String, val factIds: List<String>, val updatedAt: Instant,
)
data class ApplicationMaterialsDraftView(val draft: JsonNode, val version: Int, val updatedAt: Instant)
data class ApplicationInterviewSessionView(val session: ApplicationInterviewSession, val turns: List<ApplicationInterviewTurn>)
data class ApplicationAiTaskView(
    val id: String, val applicationId: String, val type: ApplicationAiTaskType, val status: ApplicationAiTaskStatus,
    val errorMessage: String?, val progressMessage: String?, val attempts: Int, val createdAt: Instant, val updatedAt: Instant, val result: JsonNode?,
)
data class SaveApplicationQuestionRequest(val prompt: String, val characterLimit: Int? = null, val answer: String? = null)
data class SaveApplicationAnswerRequest(val answer: String)
data class ApplicationAiWorkerTask(val taskId: String, val applicationId: String, val postingId: Long, val leaseToken: String, val taskType: ApplicationAiTaskType, val request: JsonNode)
data class ApplicationAiLeaseRequest(val leaseToken: String)
data class ApplicationAiProgressRequest(val leaseToken: String, val message: String)
data class ApplicationAiCompletionRequest(val leaseToken: String, val result: JsonNode)
data class ApplicationAiFailureRequest(val leaseToken: String, val message: String)
data class ApplicationAiTaskViewEnvelope(val task: ApplicationAiTaskView)
data class SaveApplicationMaterialsRequest(
    val resumeHeadline: String, val resumeSummary: String, val resumeSections: List<ApplicationResumeSectionEdit>, val portfolioProjects: List<ApplicationPortfolioProjectEdit>,
)
data class ApplicationResumeSectionEdit(val id: String, val heading: String, val content: String, val included: Boolean, val order: Int)
data class ApplicationPortfolioProjectEdit(val id: String, val title: String, val selectionReason: String, val summary: String, val included: Boolean, val order: Int)
data class ApplicationInterviewAnswerRequest(val answer: String)
data class PromoteInterviewExperienceRequest(val title: String, val summary: String)
