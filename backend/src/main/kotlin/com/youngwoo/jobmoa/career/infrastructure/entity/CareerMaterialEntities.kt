package com.youngwoo.jobmoa.career.infrastructure.entity

import com.youngwoo.jobmoa.career.domain.model.*
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "career_material")
open class CareerMaterialEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "original_filename", nullable = false, length = 512) open var originalFilename: String = "",
    @field:Column(name = "detected_media_type", nullable = false, length = 255) open var detectedMediaType: String = "application/octet-stream",
    @field:Column(name = "file_size", nullable = false) open var fileSize: Long = 0,
    @field:Column(name = "content_hash", nullable = false, length = 64) open var contentHash: String = "",
    @field:Column(name = "storage_key", nullable = false, length = 36) open var storageKey: String = "",
    @field:Column(nullable = false, length = 24) open var status: String = CareerMaterialStatus.WAITING_AI.name,
    @field:Column(name = "error_message", columnDefinition = "longtext") open var errorMessage: String? = null,
    @field:Column(nullable = false) open var attempts: Int = 0,
    @field:Column(name = "lease_token", length = 36) open var leaseToken: String? = null,
    @field:Column(name = "lease_expires_at") open var leaseExpiresAt: Instant? = null,
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
    @field:Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.EPOCH,
) {
    fun domain() = CareerMaterial(
        id, originalFilename, detectedMediaType, fileSize, contentHash, storageKey,
        CareerMaterialStatus.valueOf(status), errorMessage, attempts, leaseToken, leaseExpiresAt,
        createdAt, updatedAt,
    )
}

@Entity
@Table(name = "career_material_fragment")
open class CareerMaterialFragmentEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "material_id", nullable = false, length = 36) open var materialId: String = "",
    @field:Column(nullable = false) open var ordinal: Int = 0,
    @field:Column(name = "location_label", nullable = false, length = 255) open var locationLabel: String = "",
    @field:Column(nullable = false, columnDefinition = "longtext") open var content: String = "",
) {
    fun domain() = CareerMaterialFragment(id, materialId, ordinal, locationLabel, content)
}

@Entity
@Table(name = "career_fact")
open class CareerFactEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "material_id", nullable = false, length = 36) open var materialId: String = "",
    @field:Column(nullable = false, length = 32) open var kind: String = CareerFactKind.OTHER.name,
    @field:Column(nullable = false, length = 500) open var title: String = "",
    @field:Column(nullable = false, columnDefinition = "longtext") open var summary: String = "",
    @field:Column(name = "verification_status", nullable = false, length = 24) open var verificationStatus: String = CareerFactStatus.UNVERIFIED.name,
    @field:Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
    @field:Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.EPOCH,
) {
    fun domain(evidence: List<CareerFactEvidence>) = CareerFact(
        id, materialId, CareerFactKind.valueOf(kind), title, summary,
        CareerFactStatus.valueOf(verificationStatus), evidence, createdAt, updatedAt,
    )
}

@Entity
@Table(name = "career_fact_evidence")
open class CareerFactEvidenceEntity(
    @field:Id open var id: String = "",
    @field:Column(name = "fact_id", nullable = false, length = 36) open var factId: String = "",
    @field:Column(name = "fragment_id", nullable = false, length = 36) open var fragmentId: String = "",
    @field:Column(nullable = false, length = 1000) open var excerpt: String = "",
) {
    fun domain(locationLabel: String) = CareerFactEvidence(id, fragmentId, locationLabel, excerpt)
}

fun CareerMaterial.entity() = CareerMaterialEntity(
    id, originalFilename, detectedMediaType, fileSize, contentHash, storageKey, status.name,
    errorMessage, attempts, leaseToken, leaseExpiresAt, createdAt, updatedAt,
)

fun CareerMaterialFragment.entity() = CareerMaterialFragmentEntity(id, materialId, ordinal, locationLabel, content)
fun CareerFact.entity() = CareerFactEntity(id, materialId, kind.name, title, summary, status.name, createdAt, updatedAt)
fun CareerFactEvidence.entity(factId: String) = CareerFactEvidenceEntity(id, factId, fragmentId, excerpt)
