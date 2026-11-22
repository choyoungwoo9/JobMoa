package com.youngwoo.jobmoa.application.presentation

import com.youngwoo.jobmoa.application.application.*
import com.youngwoo.jobmoa.application.domain.ApplicationCaseStatus
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.json.JsonMapper

@RestController
class ApplicationPreparationController(
    private val service: ApplicationPreparationService,
    private val mapper: JsonMapper,
) {
    @GetMapping("/api/applications")
    fun list() = service.list()

    @GetMapping("/api/applications/statuses")
    fun statuses() = service.statuses()

    @PostMapping("/api/postings/{postingId}/application-case")
    fun create(@PathVariable postingId: Long) = service.create(postingId)

    @GetMapping("/api/applications/{applicationId}")
    fun detail(@PathVariable applicationId: String) = service.detail(applicationId)

    @PatchMapping("/api/applications/{applicationId}/status")
    fun updateStatus(@PathVariable applicationId: String, @RequestBody request: UpdateApplicationStatusRequest) =
        service.updateStatus(applicationId, request.status)

    @PostMapping("/api/applications/{applicationId}/questions/extract")
    fun extractQuestions(@PathVariable applicationId: String): ResponseEntity<ApplicationAiTaskView> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(service.extractQuestions(applicationId))

    @PostMapping("/api/applications/{applicationId}/questions")
    fun addQuestion(@PathVariable applicationId: String, @RequestBody request: SaveApplicationQuestionRequest) =
        service.addQuestion(applicationId, request)

    @PutMapping("/api/applications/{applicationId}/questions/{questionId}")
    fun updateQuestion(@PathVariable applicationId: String, @PathVariable questionId: String, @RequestBody request: SaveApplicationQuestionRequest) =
        service.updateQuestion(applicationId, questionId, request)

    @DeleteMapping("/api/applications/{applicationId}/questions/{questionId}")
    fun deleteQuestion(@PathVariable applicationId: String, @PathVariable questionId: String) =
        service.deleteQuestion(applicationId, questionId)

    @PutMapping("/api/applications/{applicationId}/questions/{questionId}/answer")
    fun saveAnswer(@PathVariable applicationId: String, @PathVariable questionId: String, @RequestBody request: SaveApplicationAnswerRequest) =
        service.saveAnswer(applicationId, questionId, request.answer)

    @PostMapping("/api/applications/{applicationId}/questions/{questionId}/answer-draft")
    fun draftAnswer(@PathVariable applicationId: String, @PathVariable questionId: String): ResponseEntity<ApplicationAiTaskView> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(service.draftAnswer(applicationId, questionId))

    @PostMapping("/api/applications/{applicationId}/materials/generate")
    fun generateMaterials(@PathVariable applicationId: String): ResponseEntity<ApplicationAiTaskView> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(service.generateMaterials(applicationId))

    @PutMapping("/api/applications/{applicationId}/materials")
    fun saveMaterials(@PathVariable applicationId: String, @RequestBody request: SaveApplicationMaterialsRequest) =
        service.saveMaterials(applicationId, request)

    @PostMapping("/api/applications/{applicationId}/interview/research")
    fun research(@PathVariable applicationId: String): ResponseEntity<ApplicationAiTaskView> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(service.researchInterview(applicationId))

    @PostMapping("/api/applications/{applicationId}/interview/sessions")
    fun startInterview(@PathVariable applicationId: String) = service.startInterview(applicationId)

    @PostMapping("/api/applications/{applicationId}/interview/sessions/{sessionId}/turns/{turnId}/answer")
    fun answerInterview(@PathVariable applicationId: String, @PathVariable sessionId: String, @PathVariable turnId: String,
                        @RequestBody request: ApplicationInterviewAnswerRequest): ResponseEntity<ApplicationAiTaskView> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(service.answerInterview(applicationId, sessionId, turnId, request.answer))

    @PutMapping("/api/applications/{applicationId}/interview/sessions/{sessionId}/turns/{turnId}/follow-up-answer")
    fun answerFollowUp(@PathVariable applicationId: String, @PathVariable sessionId: String, @PathVariable turnId: String,
                       @RequestBody request: ApplicationInterviewAnswerRequest) =
        service.answerFollowUp(applicationId, sessionId, turnId, request.answer)

    @PostMapping("/api/applications/{applicationId}/interview/sessions/{sessionId}/next")
    fun nextQuestion(@PathVariable applicationId: String, @PathVariable sessionId: String) =
        service.nextInterviewQuestion(applicationId, sessionId)

    @PostMapping("/api/applications/{applicationId}/interview/sessions/{sessionId}/turns/{turnId}/promote")
    fun promote(@PathVariable applicationId: String, @PathVariable sessionId: String, @PathVariable turnId: String,
                @RequestBody request: PromoteInterviewExperienceRequest) =
        service.promoteInterviewExperience(applicationId, sessionId, turnId, request)

    @GetMapping("/api/applications/{applicationId}/tasks/{taskId}")
    fun task(@PathVariable applicationId: String, @PathVariable taskId: String) = service.task(applicationId, taskId)

    @PostMapping("/api/applications/{applicationId}/tasks/{taskId}/retry")
    fun retry(@PathVariable applicationId: String, @PathVariable taskId: String) = service.retryTask(applicationId, taskId)

    @PostMapping("/api/applications/worker/tasks/claim")
    fun claim(): ApplicationAiWorkerTask? = service.claimTask()?.let { lease ->
        ApplicationAiWorkerTask(lease.task.id, lease.task.applicationId, lease.task.postingId, lease.token, lease.task.type, mapper.readTree(lease.task.requestJson))
    }

    @PostMapping("/api/applications/worker/tasks/{taskId}/renew")
    fun renew(@PathVariable taskId: String, @RequestBody request: ApplicationAiLeaseRequest) = mapOf("renewed" to service.renewTask(taskId, request.leaseToken))

    @PostMapping("/api/applications/worker/tasks/{taskId}/progress")
    fun progress(@PathVariable taskId: String, @RequestBody request: ApplicationAiProgressRequest) =
        service.progressTask(taskId, request.leaseToken, request.message)

    @PostMapping("/api/applications/worker/tasks/{taskId}/complete")
    fun complete(@PathVariable taskId: String, @RequestBody request: ApplicationAiCompletionRequest) =
        service.completeTask(taskId, request.leaseToken, request.result)

    @PostMapping("/api/applications/worker/tasks/{taskId}/fail")
    fun fail(@PathVariable taskId: String, @RequestBody request: ApplicationAiFailureRequest) =
        service.failTask(taskId, request.leaseToken, request.message)
}

data class UpdateApplicationStatusRequest(val status: ApplicationCaseStatus)
