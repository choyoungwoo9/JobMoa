package com.youngwoo.jobmoa.applicationrequirements.presentation

import com.youngwoo.jobmoa.applicationrequirements.application.ApplicationRequirementService
import com.youngwoo.jobmoa.applicationrequirements.application.ApplicationRequirementView
import com.youngwoo.jobmoa.applicationrequirements.domain.RequirementSource
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
class ApplicationRequirementController(private val service: ApplicationRequirementService) {
    @GetMapping("/api/postings/{postingId}/application-requirements")
    fun list(@PathVariable postingId: Long) = service.list(postingId)

    @PostMapping("/api/postings/{postingId}/application-requirements")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable postingId: Long, @RequestBody request: CreateRequirementRequest) =
        service.create(postingId, request.source, request.sourceUrl, request.rawText)

    @PostMapping("/api/application-requirements/{id}/confirm")
    fun confirm(@PathVariable id: String) = service.confirm(id)

    @PostMapping("/api/application-requirements/{id}/retry")
    fun retry(@PathVariable id: String) = service.retry(id)

    @PostMapping("/api/application-requirements/worker/tasks/claim")
    fun claim() = service.claim()

    @PostMapping("/api/application-requirements/worker/tasks/{id}/renew")
    fun renew(@PathVariable id: String, @RequestBody request: RequirementWorkerLeaseRequest) = mapOf("renewed" to service.renew(id, request.leaseToken))

    @PostMapping("/api/application-requirements/worker/tasks/{id}/complete")
    fun complete(@PathVariable id: String, @RequestBody request: RequirementWorkerCompletionRequest) = service.complete(id, request.leaseToken, request.result)

    @PostMapping("/api/application-requirements/worker/tasks/{id}/fail")
    fun fail(@PathVariable id: String, @RequestBody request: RequirementWorkerFailureRequest) = service.fail(id, request.leaseToken, request.message)
}

data class CreateRequirementRequest(val source: RequirementSource = RequirementSource.USER_PASTED, val sourceUrl: String? = null, val rawText: String)
data class RequirementWorkerLeaseRequest(val leaseToken: String)
data class RequirementWorkerFailureRequest(val leaseToken: String, val message: String)
data class RequirementWorkerCompletionRequest(val leaseToken: String, val result: Map<String, Any?>)
