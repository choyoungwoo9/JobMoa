package com.youngwoo.jobmoa.posting.infrastructure.repository

import com.youngwoo.jobmoa.posting.domain.model.*
import com.youngwoo.jobmoa.posting.domain.repository.PostingRepository
import com.youngwoo.jobmoa.posting.infrastructure.entity.*
import com.youngwoo.jobmoa.shared.ResourceNotFound
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional
class PostingRepositoryImpl(
    private val postings: PostingJpaRepository,
    private val versions: PostingVersionJpaRepository,
) : PostingRepository {
    override fun findByHashForUpdate(hash: String): Posting? = postings.findByUrlHash(hash)?.domain()
    override fun find(id: Long): Posting? = postings.findById(id).orElse(null)?.domain()
    override fun list(): List<Posting> = postings.findAllByOrderByFirstSeenDescIdDesc().map { it.domain() }
    override fun save(posting: Posting): Posting = postings.saveAndFlush(posting.entity()).domain()
    override fun versions(id: Long): List<PostingVersion> = versions.findAllByPostingIdOrderBySavedAtDescIdDesc(id).map { it.domain() }
    override fun versionSummaries(postingIds: List<Long>): List<VersionSummary> =
        if (postingIds.isEmpty()) emptyList() else versions.summaries(postingIds).map { VersionSummary(it.id, it.postingId, it.savedAt, it.capturePath) }
    override fun saveVersion(version: PostingVersion): PostingVersion = versions.saveAndFlush(version.entity()).domain()
    override fun markRead(id: Long, read: Boolean) {
        val entity = postings.lockById(id) ?: throw ResourceNotFound("공고를 찾을 수 없습니다.")
        entity.read = read
    }
}
