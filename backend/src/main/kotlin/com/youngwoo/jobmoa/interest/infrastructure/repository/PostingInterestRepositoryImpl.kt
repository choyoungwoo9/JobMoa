package com.youngwoo.jobmoa.interest.infrastructure.repository

import com.youngwoo.jobmoa.interest.domain.model.PostingInterest
import com.youngwoo.jobmoa.interest.domain.repository.PostingInterestRepository
import com.youngwoo.jobmoa.interest.infrastructure.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

interface PostingInterestJpaRepository : JpaRepository<PostingInterestEntity, Long>

@Repository
@Transactional
class PostingInterestRepositoryImpl(private val interests: PostingInterestJpaRepository) : PostingInterestRepository {
    override fun find(postingId: Long): PostingInterest? = interests.findById(postingId).orElse(null)?.domain()
    override fun findAll(postingIds: List<Long>): Map<Long, PostingInterest> =
        if (postingIds.isEmpty()) emptyMap() else interests.findAllById(postingIds).associate { it.postingId to it.domain() }
    override fun save(interest: PostingInterest): PostingInterest = interests.saveAndFlush(interest.entity()).domain()
}
