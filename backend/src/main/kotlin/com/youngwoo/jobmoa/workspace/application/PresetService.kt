package com.youngwoo.jobmoa.workspace.application

import com.youngwoo.jobmoa.workspace.domain.*
import com.youngwoo.jobmoa.crawler.application.CompanyRegistry
import com.youngwoo.jobmoa.crawler.application.CategoryClassifier
import com.youngwoo.jobmoa.collection.application.CollectionService
import com.youngwoo.jobmoa.shared.ResourceNotFound
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class PresetService(private val presets: PresetRepository, private val companies: CompanyRegistry,
    private val classifier: CategoryClassifier, private val collections: CollectionService, private val access: WorkspaceAccess) {
    @Transactional(readOnly = true) fun list() = presets.list()
    @Transactional fun save(id: String?, name: String, categoryIds: List<String>, companyIds: List<String>): CollectionPreset {
        access.writing()
        require(name.trim().length in 1..80) { "프리셋 이름은 1~80자여야 합니다." }
        require(companyIds.isNotEmpty() && companyIds.size <= companies.list().size) { "수집할 회사를 선택하세요." }
        val scope = categoryIds.distinct().sorted()
        require(scope.isNotEmpty() && scope.all { id -> companies.categories.any { it.id == id } } && ("all" !in scope || scope.size == 1)) { "지원하는 직군을 선택하세요. 전체 공고와 세부 직군은 함께 선택할 수 없습니다." }
        val selected = companyIds.distinct().sorted()
        require(selected.all { company -> companies.find(company) != null && scope.any { classifier.supports(company, it) } } && scope.all { category -> selected.any { classifier.supports(it, category) } }) { "해당 직군 수집을 지원하지 않는 회사가 있습니다." }
        val previous = id?.let { presets.find(it) ?: throw ResourceNotFound("프리셋을 찾을 수 없습니다.") }
        val now = Instant.now()
        return presets.save(CollectionPreset(previous?.id ?: UUID.randomUUID().toString(), name.trim(), scope, selected, previous?.createdAt ?: now, now))
    }
    @Transactional fun delete(id: String) { access.writing(); presets.find(id) ?: throw ResourceNotFound("프리셋을 찾을 수 없습니다."); presets.delete(id) }
    // collections.start가 동일한 트랜잭션에서 전역 배타 잠금을 획득한다. 공유 잠금 승격은 피한다.
    @Transactional fun start(id: String): String {
        val preset = presets.find(id) ?: throw ResourceNotFound("프리셋을 찾을 수 없습니다.")
        return collections.start(emptyList(), categoryIds = preset.categoryIds, companyIds = preset.companyIds)
    }
}
