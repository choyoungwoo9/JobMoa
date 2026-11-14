package com.youngwoo.jobmoa.posting.infrastructure.repository

import com.youngwoo.jobmoa.posting.infrastructure.entity.PostingEntity
import com.youngwoo.jobmoa.posting.infrastructure.entity.PostingVersionEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PostingJpaRepository : JpaRepository<PostingEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByUrlHash(urlHash: String): PostingEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PostingEntity p where p.id = :id")
    fun lockById(@Param("id") id: Long): PostingEntity?

    fun findAllByOrderByFirstSeenDescIdDesc(): List<PostingEntity>
}

interface PostingVersionJpaRepository : JpaRepository<PostingVersionEntity, Long> {
    fun findAllByPostingIdOrderBySavedAtDescIdDesc(postingId: Long): List<PostingVersionEntity>
}
