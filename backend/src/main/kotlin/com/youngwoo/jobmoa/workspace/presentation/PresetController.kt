package com.youngwoo.jobmoa.workspace.presentation

import com.youngwoo.jobmoa.workspace.application.PresetService
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus

@RestController @RequestMapping("/api/collection-presets")
class PresetController(private val service: PresetService) {
    @GetMapping fun list() = service.list()
    @PostMapping @ResponseStatus(HttpStatus.CREATED) fun create(@RequestBody r: PresetRequest) = service.save(null, r.name, r.categoryIds ?: listOf(r.categoryId), r.companyIds)
    @PutMapping("/{id}") fun update(@PathVariable id: String, @RequestBody r: PresetRequest) = service.save(id, r.name, r.categoryIds ?: listOf(r.categoryId), r.companyIds)
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun delete(@PathVariable id: String) = service.delete(id)
    @PostMapping("/{id}/start") @ResponseStatus(HttpStatus.ACCEPTED) fun start(@PathVariable id: String) = mapOf("id" to service.start(id))
}
data class PresetRequest(val name: String, val categoryId: String = "all", val companyIds: List<String>, val categoryIds: List<String>? = null)
