package com.youngwoo.jobmoa.posting.infrastructure.repository

import com.youngwoo.jobmoa.posting.infrastructure.entity.PostingEntity
import com.youngwoo.jobmoa.posting.infrastructure.entity.PostingVersionEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface PostingJpaRepository : JpaRepository<PostingEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByUrlHash(urlHash: String): PostingEntity?
    fun findAllByOrderByFirstSeenDescIdDesc(): List<PostingEntity>
}

interface PostingVersionJpaRepository : JpaRepository<PostingVersionEntity, Long> {
    fun findAllByPostingIdOrderBySavedAtDescIdDesc(postingId: Long): List<PostingVersionEntity>
}
