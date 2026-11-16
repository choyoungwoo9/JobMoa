package com.youngwoo.jobmoa.interest.infrastructure.entity

import com.youngwoo.jobmoa.interest.domain.model.*
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

@Entity @Table(name = "posting_interest")
open class PostingInterestEntity(
    @field:Id @field:Column(name = "posting_id") open var postingId: Long = 0,
    @field:Column(nullable = false) open var starred: Boolean = false,
    @field:Column(nullable = false, columnDefinition = "text") open var note: String = "",
    @field:Column(nullable = false, length = 16) open var priority: String = InterestPriority.NONE.name,
    @field:Column(name = "application_status", nullable = false, length = 32) open var applicationStatus: String = ApplicationStatus.BEFORE_APPLY.name,
    @field:Column(name = "applied_on") open var appliedOn: LocalDate? = null,
    @field:Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.EPOCH,
) {
    fun domain() = PostingInterest(postingId, starred, note, InterestPriority.valueOf(priority), ApplicationStatus.valueOf(applicationStatus), appliedOn, updatedAt)
}
fun PostingInterest.entity() = PostingInterestEntity(postingId, starred, note, priority.name, applicationStatus.name, appliedOn, checkNotNull(updatedAt))
