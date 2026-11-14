package com.youngwoo.jobmoa.posting.infrastructure.repository

import com.youngwoo.jobmoa.posting.domain.model.*
import com.youngwoo.jobmoa.posting.domain.repository.PostingRepository
import com.youngwoo.jobmoa.posting.infrastructure.entity.*
import com.youngwoo.jobmoa.shared.ResourceNotFound
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository @Transactional
class PostingRepositoryImpl(private val em: EntityManager) : PostingRepository {
    override fun findByHashForUpdate(hash: String): Posting? = em.createQuery(
        "select p from PostingEntity p where p.urlHash = :hash", PostingEntity::class.java
    ).setParameter("hash", hash).setLockMode(LockModeType.PESSIMISTIC_WRITE).resultList.firstOrNull()?.domain()

    @Transactional(readOnly = true)
    override fun find(id: Long): Posting? = em.find(PostingEntity::class.java, id)?.domain()
    @Transactional(readOnly = true)
    override fun list(): List<Posting> = em.createQuery("select p from PostingEntity p order by p.firstSeen desc, p.id desc", PostingEntity::class.java)
        .resultList.map { it.domain() }

    override fun save(posting: Posting): Posting {
        val entity = posting.entity()
        if (posting.id == 0L) { em.persist(entity); em.flush(); return entity.domain() }
        return em.merge(entity).domain()
    }

    @Transactional(readOnly = true)
    override fun versions(id: Long): List<PostingVersion> = em.createQuery(
        "select v from PostingVersionEntity v where v.postingId = :id order by v.savedAt desc, v.id desc", PostingVersionEntity::class.java
    ).setParameter("id", id).resultList.map { it.domain() }

    override fun saveVersion(version: PostingVersion): PostingVersion {
        val entity = version.entity()
        em.persist(entity); em.flush()
        return entity.domain()
    }

    override fun markRead(id: Long, read: Boolean) {
        val entity = em.find(PostingEntity::class.java, id) ?: throw ResourceNotFound("공고를 찾을 수 없습니다.")
        entity.read = read
    }
}
