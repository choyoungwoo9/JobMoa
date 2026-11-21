package com.youngwoo.jobmoa.aiqa.presentation

import com.youngwoo.jobmoa.aiqa.application.AiChatService
import com.youngwoo.jobmoa.aiqa.domain.model.AiCitation
import com.youngwoo.jobmoa.aiqa.domain.model.AiCitationType
import com.youngwoo.jobmoa.aiqa.domain.model.AiWorkerResult
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ai")
class AiChatController(private val service: AiChatService) {
    @GetMapping("/status") fun status() = service.workerStatus()

    @GetMapping("/conversations") fun conversations() = service.conversations()

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    fun createConversation() = service.createConversation()

    @GetMapping("/conversations/{id}") fun conversation(@PathVariable id: String) = service.conversation(id)

    @GetMapping("/tasks/{id}/events")
    fun events(
        @PathVariable id: String,
        @RequestParam(defaultValue = "0") after: Long,
        @RequestHeader(name = "Last-Event-ID", required = false) lastEventId: String?,
    ) = service.events(id, lastEventId?.toLongOrNull() ?: after)

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteConversation(@PathVariable id: String) = service.deleteConversation(id)

    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun sendMessage(@PathVariable id: String, @RequestBody request: AiMessageRequest) =
        service.sendMessage(id, request.content)

    @PostMapping("/messages/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun retry(@PathVariable id: String) = service.retry(id)

    @PostMapping("/worker/heartbeat")
    fun heartbeat(@RequestBody request: WorkerHeartbeatRequest) = service.heartbeat(request.workerId, request.model)

    @PostMapping("/worker/tasks/claim") fun claim() = service.claim()

    @PostMapping("/worker/tasks/{id}/renew")
    fun renew(@PathVariable id: String, @RequestBody request: WorkerLeaseRequest) =
        mapOf("renewed" to service.renew(id, request.leaseToken))

    @PostMapping("/worker/tasks/{id}/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun progress(@PathVariable id: String, @RequestBody request: WorkerProgressRequest) =
        service.progress(id, request.leaseToken, request.type, request.payload)

    @PostMapping("/worker/tasks/{id}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun complete(@PathVariable id: String, @RequestBody request: WorkerCompletionRequest) {
        service.complete(id, request.leaseToken, AiWorkerResult(
            request.answer,
            request.postingCitationIds,
            request.webSources.map { AiCitation(AiCitationType.WEB, title = it.title, url = it.url, reason = it.reason) },
            request.searchPerformed,
            request.workspaceUsed,
        ))
    }

    @PostMapping("/worker/tasks/{id}/fail")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun fail(@PathVariable id: String, @RequestBody request: WorkerFailureRequest) =
        service.fail(id, request.leaseToken, request.message)
}

data class AiMessageRequest(val content: String)
data class WorkerHeartbeatRequest(val workerId: String, val model: String)
data class WorkerLeaseRequest(val leaseToken: String)
data class WorkerProgressRequest(val leaseToken: String, val type: String, val payload: Map<String, String>)
data class WebCitationRequest(val title: String, val url: String, val reason: String? = null)
data class WorkerCompletionRequest(
    val leaseToken: String,
    val answer: String,
    val postingCitationIds: List<Long> = emptyList(),
    val webSources: List<WebCitationRequest> = emptyList(),
    val searchPerformed: Boolean = false,
    val workspaceUsed: Boolean = true,
)
data class WorkerFailureRequest(val leaseToken: String, val message: String)
