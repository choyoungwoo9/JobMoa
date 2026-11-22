package com.youngwoo.jobmoa.interest.presentation

import com.youngwoo.jobmoa.interest.application.PostingInterestService
import com.youngwoo.jobmoa.interest.domain.model.*
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/postings/{id}/interest")
class PostingInterestController(private val service: PostingInterestService) {
    @PatchMapping
    fun star(@PathVariable id: Long, @RequestBody body: StarRequest) = service.star(id, body.starred)

    @PutMapping("/management")
    fun manage(@PathVariable id: Long, @RequestBody body: ManageInterestRequest) =
        service.manage(id, body.note, body.priority)
}
data class StarRequest(val starred: Boolean)
data class ManageInterestRequest(
    val note: String,
    val priority: InterestPriority,
)
