package com.youngwoo.jobmoa.career.assistant.presentation

import com.youngwoo.jobmoa.career.assistant.application.CareerAssistantService
import com.youngwoo.jobmoa.career.assistant.application.CareerAssistantTaskView
import com.youngwoo.jobmoa.career.assistant.application.CareerRecommendationRequest
import com.youngwoo.jobmoa.career.assistant.domain.model.CareerAssistantTaskType
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@RestController
@RequestMapping("/api/career-assistant")
class CareerAssistantController(
    private val service: CareerAssistantService,
    private val mapper: JsonMapper,
) {
    @PostMapping("/recommendations")
    fun recommend(@RequestBody request: CareerRecommendationRequest): ResponseEntity<CareerAssistantTaskView> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(service.recommend(request))

    @GetMapping("/readiness")
    fun readiness() = service.readiness()

    @GetMapping("/recommendations")
    fun recommendations() = service.recentRecommendations()

    @GetMapping("/recommendations/{id}/drafts")
    fun drafts(@PathVariable id: String) = service.drafts(id)

    @PostMapping("/recommendations/{id}/drafts")
    fun createDraft(@PathVariable id: String, @RequestBody request: CareerPostingDraftRequest): ResponseEntity<CareerAssistantTaskView> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(service.createDraft(id, request.postingId))

    @GetMapping("/tasks/{id}")
    fun task(@PathVariable id: String) = service.task(id)

    @PostMapping("/tasks/{id}/retry")
    fun retry(@PathVariable id: String) = service.retry(id)

    @PostMapping("/worker/tasks/claim")
    fun claim(): CareerAssistantWorkerTask? = service.claim()?.let { lease ->
        CareerAssistantWorkerTask(
            taskId = lease.task.id,
            leaseToken = lease.token,
            taskType = lease.task.type,
            request = mapper.readTree(lease.task.requestJson),
        )
    }

    @PostMapping("/worker/tasks/{id}/renew")
    fun renew(@PathVariable id: String, @RequestBody request: CareerAssistantLeaseRequest) =
        mapOf("renewed" to service.renew(id, request.leaseToken))

    @PostMapping("/worker/tasks/{id}/complete")
    fun complete(@PathVariable id: String, @RequestBody request: CareerAssistantCompletionRequest) =
        service.complete(id, request.leaseToken, request.result)

    @PostMapping("/worker/tasks/{id}/fail")
    fun fail(@PathVariable id: String, @RequestBody request: CareerAssistantFailureRequest) =
        service.fail(id, request.leaseToken, request.message)
}

data class CareerPostingDraftRequest(val postingId: Long)
data class CareerAssistantLeaseRequest(val leaseToken: String)
data class CareerAssistantCompletionRequest(val leaseToken: String, val result: JsonNode)
data class CareerAssistantFailureRequest(val leaseToken: String, val message: String)
data class CareerAssistantWorkerTask(
    val taskId: String,
    val leaseToken: String,
    val taskType: CareerAssistantTaskType,
    val request: JsonNode,
)
