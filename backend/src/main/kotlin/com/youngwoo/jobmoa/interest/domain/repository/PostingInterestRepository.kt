package com.youngwoo.jobmoa.interest.domain.repository

import com.youngwoo.jobmoa.interest.domain.model.PostingInterest

interface PostingInterestRepository {
    fun find(postingId: Long): PostingInterest?
    fun findAll(postingIds: List<Long>): Map<Long, PostingInterest>
    fun save(interest: PostingInterest): PostingInterest
}
