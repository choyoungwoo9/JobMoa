package com.youngwoo.jobmoa.interest.domain.model

import java.time.Instant
import java.time.LocalDate

enum class InterestPriority { HIGH, NORMAL, LOW, NONE }
enum class ApplicationStatus { BEFORE_APPLY, APPLIED }

/** 수집 결과·본문 버전과 독립적인 공고 단위의 개인 관리 정보. */
data class PostingInterest(
    val postingId: Long,
    val starred: Boolean = false,
    val note: String = "",
    val priority: InterestPriority = InterestPriority.NONE,
    val applicationStatus: ApplicationStatus = ApplicationStatus.BEFORE_APPLY,
    val appliedOn: LocalDate? = null,
    val updatedAt: Instant? = null,
) {
    fun star(value: Boolean, now: Instant) = copy(starred = value, updatedAt = now)

    fun manage(note: String, priority: InterestPriority, now: Instant): PostingInterest {
        require(note.length <= 5000) { "메모는 5,000자까지 저장할 수 있습니다." }
        return copy(note = note, priority = priority, updatedAt = now)
    }
}
