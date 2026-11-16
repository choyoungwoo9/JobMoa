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

    fun manage(note: String, priority: InterestPriority, status: ApplicationStatus, appliedOn: LocalDate?, today: LocalDate, now: Instant): PostingInterest {
        require(note.length <= 5000) { "메모는 5,000자까지 저장할 수 있습니다." }
        require(status == ApplicationStatus.APPLIED || appliedOn == null) { "지원 전 공고에는 지원일을 지정할 수 없습니다." }
        val date = if (status == ApplicationStatus.APPLIED) appliedOn ?: this.appliedOn ?: today else null
        require(date == null || !date.isAfter(today)) { "지원일은 오늘 이후로 지정할 수 없습니다." }
        return copy(note = note, priority = priority, applicationStatus = status, appliedOn = date, updatedAt = now)
    }
}
