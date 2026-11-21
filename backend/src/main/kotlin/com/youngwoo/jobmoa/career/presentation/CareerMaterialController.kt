package com.youngwoo.jobmoa.career.presentation

import com.youngwoo.jobmoa.career.application.CareerMaterialService
import com.youngwoo.jobmoa.career.domain.model.*
import org.springframework.core.io.InputStreamResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpStatus
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/career-materials")
class CareerMaterialController(private val service: CareerMaterialService) {
    @GetMapping fun list() = service.list()

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestPart("files") files: List<MultipartFile>) = service.upload(files)

    @GetMapping("/worker/status") fun workerStatus() = service.workerStatus()

    @GetMapping("/{id}") fun detail(@PathVariable id: String) = service.detail(id)

    @GetMapping("/{id}/original")
    fun original(@PathVariable id: String): ResponseEntity<InputStreamResource> {
        val (path, filename) = service.originalPath(id)
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_OCTET_STREAM
            contentLength = java.nio.file.Files.size(path)
            contentDisposition = ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build()
        }
        return ResponseEntity.ok().headers(headers).body(InputStreamResource(java.nio.file.Files.newInputStream(path)))
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) = service.delete(id)

    @PostMapping("/{id}/retry")
    fun retry(@PathVariable id: String) = service.retry(id)

    @PostMapping("/worker/tasks/claim")
    fun claim(): CareerWorkerTask? = service.claim()?.let { lease ->
        CareerWorkerTask(lease.material.id, lease.leaseToken, lease.material.originalFilename, lease.material.detectedMediaType, lease.fragments)
    }

    @PostMapping("/worker/tasks/{id}/renew")
    fun renew(@PathVariable id: String, @RequestBody request: CareerWorkerLeaseRequest) = mapOf("renewed" to service.renew(id, request.leaseToken))

    @PostMapping("/worker/tasks/{id}/complete")
    fun complete(@PathVariable id: String, @RequestBody request: CareerWorkerCompletionRequest) =
        service.complete(id, request.leaseToken, CareerAnalysisResult(request.facts.map { fact ->
            CareerFactDraft(
                kind = runCatching { CareerFactKind.valueOf(fact.kind.uppercase()) }.getOrDefault(CareerFactKind.OTHER),
                title = fact.title,
                summary = fact.summary,
                evidence = fact.evidence.map { CareerFactEvidenceDraft(it.fragmentId, it.excerpt) },
            )
        }))

    @PostMapping("/worker/tasks/{id}/fail")
    fun fail(@PathVariable id: String, @RequestBody request: CareerWorkerFailureRequest) = service.fail(id, request.leaseToken, request.message)
}

data class CareerWorkerTask(
    val taskId: String,
    val leaseToken: String,
    val filename: String,
    val mediaType: String,
    val fragments: List<CareerMaterialFragment>,
)
data class CareerWorkerLeaseRequest(val leaseToken: String)
data class CareerWorkerFailureRequest(val leaseToken: String, val message: String)
data class CareerWorkerCompletionRequest(val leaseToken: String, val facts: List<CareerFactRequest> = emptyList())
data class CareerFactRequest(val kind: String, val title: String, val summary: String, val evidence: List<CareerFactEvidenceRequest> = emptyList())
data class CareerFactEvidenceRequest(val fragmentId: String, val excerpt: String)
