package com.youngwoo.jobmoa.career.domain.model

import java.time.Instant

enum class CareerMaterialStatus { WAITING_AI, ANALYZING, ANALYZED, FAILED, UNREADABLE }
enum class CareerFactKind { EXPERIENCE, PROJECT, TECHNOLOGY, ACHIEVEMENT, EDUCATION, OTHER }
enum class CareerFactStatus { UNVERIFIED, VERIFIED, REJECTED }

data class CareerMaterial(
    val id: String,
    val originalFilename: String,
    val detectedMediaType: String,
    val fileSize: Long,
    val contentHash: String,
    val storageKey: String,
    val status: CareerMaterialStatus,
    val errorMessage: String? = null,
    val attempts: Int = 0,
    val leaseToken: String? = null,
    val leaseExpiresAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CareerMaterialFragment(
    val id: String,
    val materialId: String,
    val ordinal: Int,
    val locationLabel: String,
    val content: String,
)

data class CareerFactEvidence(
    val id: String,
    val fragmentId: String,
    val locationLabel: String,
    val excerpt: String,
)

data class CareerFact(
    val id: String,
    val materialId: String,
    val kind: CareerFactKind,
    val title: String,
    val summary: String,
    val status: CareerFactStatus,
    val evidence: List<CareerFactEvidence>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CareerMaterialLease(
    val material: CareerMaterial,
    val leaseToken: String,
    val fragments: List<CareerMaterialFragment>,
)

data class CareerFactDraft(
    val kind: CareerFactKind,
    val title: String,
    val summary: String,
    val evidence: List<CareerFactEvidenceDraft>,
)

data class CareerFactEvidenceDraft(val fragmentId: String, val excerpt: String)

data class CareerAnalysisResult(val facts: List<CareerFactDraft>)
