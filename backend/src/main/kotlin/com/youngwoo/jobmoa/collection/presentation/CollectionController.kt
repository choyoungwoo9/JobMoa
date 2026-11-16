package com.youngwoo.jobmoa.collection.presentation

import com.youngwoo.jobmoa.collection.application.*
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController @RequestMapping("/api")
class CollectionController(private val service: CollectionService, private val query: WorkspaceQuery) {
    @GetMapping("/state") fun state() = query.state()
    @GetMapping("/sources") fun sources() = query.sources()
    @GetMapping("/collections") fun runs() = query.collections()
    @GetMapping("/collections/{id}") fun run(@PathVariable id: String) = query.collection(id)
    @GetMapping("/collections/{id}/changes") fun changes(@PathVariable id: String) = query.changes(id)
    @GetMapping("/collections/{id}/tasks") fun tasks(@PathVariable id: String) = query.taskList(id)
    @PostMapping("/collections") @ResponseStatus(HttpStatus.ACCEPTED)
    fun start(@RequestBody request: StartCollectionRequest): Map<String, String> = mapOf("id" to service.start(request.sourceIds, request.targets, request.categoryId, request.companyIds, request.categoryIds))
    @PostMapping("/collections/{id}/retry") @ResponseStatus(HttpStatus.ACCEPTED)
    fun retry(@PathVariable id: String, @RequestBody request: RetryRequest): Map<String, String> = mapOf("id" to service.retry(id, request.failureIds))
}
data class StartCollectionRequest(
    val sourceIds: List<String> = emptyList(), val targets: List<DirectTarget> = emptyList(),
    val categoryId: String = "all", val companyIds: List<String>? = null, val categoryIds: List<String>? = null,
)
data class RetryRequest(val failureIds: List<String>? = null)
