package com.youngwoo.jobmoa.posting.infrastructure.repository

import com.youngwoo.jobmoa.posting.infrastructure.entity.PostingEntity
import com.youngwoo.jobmoa.posting.infrastructure.entity.PostingVersionEntity
import com.youngwoo.jobmoa.posting.infrastructure.entity.PostingCategoryEntity
import com.youngwoo.jobmoa.posting.infrastructure.entity.PostingCategoryId
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface PostingJpaRepository : JpaRepository<PostingEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByUrlHash(urlHash: String): PostingEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PostingEntity p where p.id = :id")
    fun lockById(@Param("id") id: Long): PostingEntity?

    fun findAllByOrderByFirstSeenDescIdDesc(): List<PostingEntity>
}

interface PostingVersionJpaRepository : JpaRepository<PostingVersionEntity, Long> {
    @Query("""select v.id as id, v.postingId as postingId, v.savedAt as savedAt, v.capturePath as capturePath
        from PostingVersionEntity v where v.postingId in :ids order by v.savedAt desc, v.id desc""")
    fun summaries(@Param("ids") postingIds: List<Long>): List<VersionSummaryProjection>
    fun findAllByPostingIdOrderBySavedAtDescIdDesc(postingId: Long): List<PostingVersionEntity>
    fun findFirstByPostingIdOrderBySavedAtDescIdDesc(postingId: Long): PostingVersionEntity?
}

interface PostingCategoryJpaRepository : JpaRepository<PostingCategoryEntity, PostingCategoryId> {
    fun findAllByIdPostingIdIn(postingIds: List<Long>): List<PostingCategoryEntity>
}

interface VersionSummaryProjection {
    val id: Long; val postingId: Long; val savedAt: Instant; val capturePath: String
}
