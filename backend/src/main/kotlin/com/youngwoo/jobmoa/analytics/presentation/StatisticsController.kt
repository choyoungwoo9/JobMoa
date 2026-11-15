package com.youngwoo.jobmoa.analytics.presentation

import com.youngwoo.jobmoa.analytics.application.StatisticsQuery
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController @RequestMapping("/api/statistics")
class StatisticsController(private val query: StatisticsQuery) {
    @GetMapping
    fun report(@RequestParam(required = false) from: LocalDate?, @RequestParam(required = false) to: LocalDate?,
        @RequestParam(required = false) sourceId: String?, @RequestParam(required = false) companyId: String?,
        @RequestParam(required = false) categoryId: String?, @RequestParam(defaultValue = "source") groupBy: String) =
        query.report(from, to, sourceId, companyId, categoryId, groupBy)
}
