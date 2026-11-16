package com.youngwoo.jobmoa.workspace.infrastructure

import com.youngwoo.jobmoa.workspace.domain.*
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Entity @Table(name = "collection_preset")
class PresetEntity(@Id var id: String = "", var name: String = "", @Column(columnDefinition = "TEXT") var categoryIds: String = "all",
    @Column(columnDefinition = "TEXT") var companyIds: String = "",
    var createdAt: Instant = Instant.EPOCH, var updatedAt: Instant = Instant.EPOCH)
interface PresetJpaRepository : JpaRepository<PresetEntity, String> {
    fun findAllByOrderByUpdatedAtDescIdAsc(): List<PresetEntity>
}
@Repository
class PresetPersistence(private val jpa: PresetJpaRepository) : PresetRepository {
    private fun PresetEntity.model() = CollectionPreset(id, name, categoryIds.split(","), companyIds.split(",").filter { it.isNotBlank() }, createdAt, updatedAt)
    override fun list() = jpa.findAllByOrderByUpdatedAtDescIdAsc().map { it.model() }
    override fun find(id: String) = jpa.findById(id).orElse(null)?.model()
    override fun save(preset: CollectionPreset): CollectionPreset = jpa.save(PresetEntity(preset.id, preset.name, preset.categoryIds.joinToString(","),
        preset.companyIds.joinToString(","), preset.createdAt, preset.updatedAt)).model()
    override fun delete(id: String) = jpa.deleteById(id)
}
