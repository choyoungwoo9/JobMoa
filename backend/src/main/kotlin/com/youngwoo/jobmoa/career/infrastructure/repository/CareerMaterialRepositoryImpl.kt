package com.youngwoo.jobmoa.career.infrastructure.repository

import com.youngwoo.jobmoa.career.domain.model.*
import com.youngwoo.jobmoa.career.domain.repository.CareerMaterialRepository
import com.youngwoo.jobmoa.career.infrastructure.entity.*
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Repository
@Transactional
class CareerMaterialRepositoryImpl(
    private val materials: CareerMaterialJpaRepository,
    private val fragments: CareerMaterialFragmentJpaRepository,
    private val facts: CareerFactJpaRepository,
    private val evidence: CareerFactEvidenceJpaRepository,
) : CareerMaterialRepository {
    override fun list() = materials.findAllByOrderByCreatedAtDescIdDesc().map { it.domain() }
    override fun find(id: String) = materials.findById(id).orElse(null)?.domain()
    override fun lock(id: String) = materials.lockById(id)?.domain()
    override fun findByHash(hash: String) = materials.findByContentHash(hash)?.domain()
    override fun save(material: CareerMaterial) { materials.save(material.entity()) }
    override fun fragments(materialId: String) = fragments.findAllByMaterialIdOrderByOrdinalAsc(materialId).map { it.domain() }
    override fun saveFragments(rows: List<CareerMaterialFragment>) { if (rows.isNotEmpty()) fragments.saveAll(rows.map { it.entity() }) }

    override fun facts(materialId: String): List<CareerFact> {
        val sourceById = fragments.findAllByMaterialIdOrderByOrdinalAsc(materialId).associateBy { it.id }
        return facts.findAllByMaterialIdOrderByCreatedAtAscIdAsc(materialId).map { fact ->
            val sources = evidence.findAllByFactIdOrderByIdAsc(fact.id).mapNotNull { row ->
                sourceById[row.fragmentId]?.let { row.domain(it.locationLabel) }
            }
            fact.domain(sources)
        }
    }

    override fun verifiedFacts(): List<CareerFact> {
        val rows = facts.findAllByVerificationStatusOrderByCreatedAtAscIdAsc(CareerFactStatus.VERIFIED.name)
        if (rows.isEmpty()) return emptyList()
        val materialIds = rows.map { it.materialId }.distinct()
        val sourceById = fragments.findAllByMaterialIdIn(materialIds).associateBy { it.id }
        val evidenceByFact = evidence.findAllByFactIdInOrderByFactIdAscIdAsc(rows.map { it.id })
            .groupBy { it.factId }
        return rows.map { fact ->
            val sources = evidenceByFact[fact.id].orEmpty().mapNotNull { row ->
                sourceById[row.fragmentId]?.let { row.domain(it.locationLabel) }
            }
            fact.domain(sources)
        }
    }

    override fun saveFact(fact: CareerFact) { facts.save(fact.entity()) }

    override fun replaceFacts(materialId: String, rows: List<CareerFact>, now: Instant) {
        facts.deleteAllForMaterial(materialId)
        if (rows.isEmpty()) return
        val saved = facts.saveAll(rows.map { it.copy(createdAt = now, updatedAt = now).entity() })
        val evidenceRows = rows.zip(saved).flatMap { (fact, entity) -> fact.evidence.map { it.entity(entity.id) } }
        if (evidenceRows.isNotEmpty()) evidence.saveAll(evidenceRows)
    }

    override fun claimNext(now: Instant, lease: Duration): CareerMaterialLease? {
        val row = materials.nextForUpdate(now) ?: return null
        row.status = CareerMaterialStatus.ANALYZING.name
        row.attempts += 1
        row.leaseToken = UUID.randomUUID().toString()
        row.leaseExpiresAt = now.plus(lease)
        row.updatedAt = now
        val saved = materials.saveAndFlush(row).domain()
        val sourceRows = fragments.findAllByMaterialIdOrderByOrdinalAsc(row.id).map { it.domain() }
        return CareerMaterialLease(saved, requireNotNull(saved.leaseToken), sourceRows)
    }

    override fun renew(id: String, token: String, now: Instant, lease: Duration) =
        materials.renew(id, token, now, now.plus(lease)) == 1

    override fun delete(id: String) { materials.deleteById(id) }

    override fun deleteAll(): List<String> {
        val keys = materials.findAll().map { it.storageKey }
        materials.deleteAll()
        return keys
    }
}
