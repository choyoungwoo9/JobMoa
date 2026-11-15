package com.youngwoo.jobmoa.analytics.domain.repository

import com.youngwoo.jobmoa.analytics.domain.model.PostingObservation

interface ObservationRepository {
    fun append(observation: PostingObservation)
    fun list(postingIds: List<Long>): List<PostingObservation>
}
