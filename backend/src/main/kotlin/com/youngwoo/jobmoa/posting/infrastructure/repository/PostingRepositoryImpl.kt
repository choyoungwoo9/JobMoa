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
    private val categories: PostingCategoryJpaRepository,
    private val mapper: tools.jackson.databind.json.JsonMapper,
) : PostingRepository {
    override fun findByHashForUpdate(hash: String): Posting? = postings.findByUrlHash(hash)?.let { withCategories(listOf(it)).single() }
    override fun lock(id: Long): Posting? = postings.lockById(id)?.let { withCategories(listOf(it)).single() }
    override fun find(id: Long): Posting? = postings.findById(id).orElse(null)?.let { withCategories(listOf(it)).single() }
    override fun list(): List<Posting> = withCategories(postings.findAllByOrderByFirstSeenDescIdDesc())
    override fun save(posting: Posting): Posting {
        val saved = postings.saveAndFlush(posting.entity().also { entity ->
            entity.classificationJson = posting.classification?.let { mapper.writeValueAsString(it) }
        })
        val links = categories.findAllByIdPostingIdIn(listOf(saved.id!!))
        val existing = links.map { it.id.categoryId }.toSet()
        val desired = posting.categories - "all"
        categories.deleteAll(links.filter { it.id.categoryId !in desired })
        val additions = desired.filter { it !in existing }
        require(additions.all { it.isNotBlank() && it.length <= 64 }) { "잘못된 공고 분야입니다." }
        if (additions.isNotEmpty()) categories.saveAllAndFlush(additions.map { PostingCategoryEntity(PostingCategoryId(saved.id!!, it)) })
        return saved.domain(desired, posting.classification)
    }
    override fun versions(id: Long): List<PostingVersion> = versions.findAllByPostingIdOrderBySavedAtDescIdDesc(id).map { it.domain() }
    override fun latestVersion(postingId: Long): PostingVersion? = versions.findFirstByPostingIdOrderBySavedAtDescIdDesc(postingId)?.domain()
    override fun findVersion(id: Long): PostingVersion? = versions.findById(id).orElse(null)?.domain()
    override fun versionSummaries(postingIds: List<Long>): List<VersionSummary> =
        if (postingIds.isEmpty()) emptyList() else versions.summaries(postingIds).map { VersionSummary(it.id, it.postingId, it.savedAt, it.capturePath) }
    override fun saveVersion(version: PostingVersion): PostingVersion = versions.saveAndFlush(version.entity()).domain()
    override fun markRead(id: Long, read: Boolean) {
        val entity = postings.lockById(id) ?: throw ResourceNotFound("공고를 찾을 수 없습니다.")
        entity.read = read
    }

    private fun withCategories(entities: List<PostingEntity>): List<Posting> {
        if (entities.isEmpty()) return emptyList()
        val byPosting = categories.findAllByIdPostingIdIn(entities.map { it.id!! })
            .groupBy({ it.id.postingId }, { it.id.categoryId })
        return entities.map { entity -> entity.domain(byPosting[entity.id].orEmpty().toSet(), entity.classificationJson?.let { mapper.readValue(it, com.youngwoo.jobmoa.classification.domain.model.Classification::class.java) }) }
    }
}
