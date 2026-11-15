package com.youngwoo.jobmoa.analytics.domain.model

import java.time.Instant

data class PostingObservation(
    val id: Long = 0,
    val postingId: Long,
    val taskId: String? = null,
    val observedAt: Instant,
    val closed: Boolean,
    val baseline: Boolean = false,
)
