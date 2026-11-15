package com.youngwoo.jobmoa.analytics.infrastructure

import com.youngwoo.jobmoa.analytics.domain.model.PostingObservation
import com.youngwoo.jobmoa.analytics.domain.repository.ObservationRepository
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Entity @Table(name = "posting_observation")
open class PostingObservationEntity(
    @field:Id @field:GeneratedValue(strategy = GenerationType.IDENTITY) open var id: Long? = null,
    @field:Column(name = "posting_id", nullable = false) open var postingId: Long = 0,
    @field:Column(name = "task_id", unique = true, length = 36) open var taskId: String? = null,
    @field:Column(name = "observed_at", nullable = false) open var observedAt: Instant = Instant.EPOCH,
    @field:Column(nullable = false) open var closed: Boolean = false,
    @field:Column(nullable = false) open var baseline: Boolean = false,
) {
    fun domain() = PostingObservation(id!!, postingId, taskId, observedAt, closed, baseline)
}

interface PostingObservationJpaRepository : JpaRepository<PostingObservationEntity, Long> {
    fun findAllByPostingIdInOrderByObservedAtAscIdAsc(postingIds: List<Long>): List<PostingObservationEntity>
}

@Repository
class ObservationRepositoryImpl(private val repository: PostingObservationJpaRepository) : ObservationRepository {
    override fun append(observation: PostingObservation) {
        repository.save(PostingObservationEntity(postingId = observation.postingId, taskId = observation.taskId,
            observedAt = observation.observedAt, closed = observation.closed, baseline = observation.baseline))
    }
    override fun list(postingIds: List<Long>): List<PostingObservation> =
        postingIds.chunked(1000).flatMap { repository.findAllByPostingIdInOrderByObservedAtAscIdAsc(it) }.map { it.domain() }
}
