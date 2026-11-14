package com.youngwoo.jobmoa.posting.presentation

import com.youngwoo.jobmoa.collection.application.WorkspaceQuery
import com.youngwoo.jobmoa.posting.application.PostingService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController @RequestMapping("/api/postings")
class PostingController(private val service: PostingService, private val query: WorkspaceQuery) {
    @GetMapping fun list() = query.jobs()
    @GetMapping("/{id}") fun detail(@PathVariable id: Long) = query.job(id)
    @PatchMapping("/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun read(@PathVariable id: Long, @RequestBody body: ReadRequest) { service.markRead(id, body.read) }
}
data class ReadRequest(val read: Boolean)
