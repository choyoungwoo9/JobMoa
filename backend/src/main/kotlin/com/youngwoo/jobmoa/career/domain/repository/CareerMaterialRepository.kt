package com.youngwoo.jobmoa.career.domain.repository

import com.youngwoo.jobmoa.career.domain.model.*
import java.time.Duration
import java.time.Instant

interface CareerMaterialRepository {
    fun list(): List<CareerMaterial>
    fun find(id: String): CareerMaterial?
    fun lock(id: String): CareerMaterial?
    fun findByHash(hash: String): CareerMaterial?
    fun save(material: CareerMaterial)
    fun fragments(materialId: String): List<CareerMaterialFragment>
    fun saveFragments(rows: List<CareerMaterialFragment>)
    fun facts(materialId: String): List<CareerFact>
    fun verifiedFacts(): List<CareerFact>
    fun saveFact(fact: CareerFact)
    fun replaceFacts(materialId: String, rows: List<CareerFact>, now: Instant)
    fun claimNext(now: Instant, lease: Duration): CareerMaterialLease?
    fun renew(id: String, token: String, now: Instant, lease: Duration): Boolean
    fun delete(id: String)
    fun deleteAll(): List<String>
}
