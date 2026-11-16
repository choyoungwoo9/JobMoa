package com.youngwoo.jobmoa.workspace.domain

import java.time.Instant

data class CollectionPreset(val id: String, val name: String, val categoryIds: List<String>,
    val companyIds: List<String>, val createdAt: Instant, val updatedAt: Instant)
interface PresetRepository {
    fun list(): List<CollectionPreset>
    fun find(id: String): CollectionPreset?
    fun save(preset: CollectionPreset): CollectionPreset
    fun delete(id: String)
}
