package com.youngwoo.jobmoa.applicationrequirements.presentation

import com.youngwoo.jobmoa.applicationrequirements.application.ApplicationRequirementService
import com.youngwoo.jobmoa.applicationrequirements.application.ApplicationRequirementView
import com.youngwoo.jobmoa.applicationrequirements.domain.RequirementSource
import org.springframework.web.bind.annotation.*

@RestController
class ApplicationRequirementController(private val service: ApplicationRequirementService) {
    @GetMapping("/api/postings/{postingId}/application-requirements")
    fun list(@PathVariable postingId: Long) = service.list(postingId)

    @PostMapping("/api/postings/{postingId}/application-requirements")
    fun create(@PathVariable postingId: Long, @RequestBody request: CreateRequirementRequest) =
        service.create(postingId, request.source, request.sourceUrl, request.rawText)
}

data class CreateRequirementRequest(val source: RequirementSource = RequirementSource.USER_PASTED, val sourceUrl: String? = null, val rawText: String)
