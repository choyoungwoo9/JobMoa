package com.youngwoo.jobmoa.analytics.application

import com.youngwoo.jobmoa.analytics.domain.model.*
import com.youngwoo.jobmoa.analytics.domain.repository.ObservationRepository
import com.youngwoo.jobmoa.crawler.application.CompanyRegistry
import com.youngwoo.jobmoa.crawler.application.SourceRegistry
import com.youngwoo.jobmoa.posting.domain.repository.PostingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Isolation
import java.time.*
import java.time.temporal.ChronoUnit

@Service
class StatisticsQuery(private val postings: PostingRepository, private val observations: ObservationRepository,
    private val companies: CompanyRegistry, private val sources: SourceRegistry) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun report(from: LocalDate?, to: LocalDate?, sourceId: String?, companyId: String?, categoryId: String?, groupBy: String): StatisticsReport {
        val now = Instant.now()
        val today = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate()
        val until = to ?: today
        val since = from ?: until.minusDays(29)
        require(since <= until && until <= today && ChronoUnit.DAYS.between(since, until) < 366) { "기간은 오늘까지 최대 366일로 선택해주세요." }
        require(groupBy in setOf("source", "company", "category")) { "지원하지 않는 통계 기준입니다." }
        val source = sourceId?.takeUnless { it == "all" }
        val company = companyId?.takeUnless { it == "all" }
        val category = categoryId?.takeUnless { it == "all" }
        require(source == null || sources.definitions.any { it.id == source }) { "지원하지 않는 사이트입니다." }
        require(company == null || companies.find(company) != null) { "지원하지 않는 회사입니다." }
        require(category == null || category == "unclassified" || companies.categories.any { it.id == category }) { "지원하지 않는 분야입니다." }
        require(source == null || company == null || companies.require(company).sourceId == source) { "사이트와 회사가 일치하지 않습니다." }
        val rows = postings.list().map { posting ->
            val definition = companies.resolve(posting.sourceId, posting.company, posting.url).firstOrNull()
            StatisticsPosting(posting.id, posting.sourceId, definition?.id ?: "${posting.sourceId}:${posting.company}",
                definition?.name ?: posting.company, posting.categories, posting.firstSeen, posting.lastSeen, posting.closed)
        }.filter { (source == null || it.sourceId == source) && (company == null || it.companyId == company) &&
            (category == null || if (category == "unclassified") it.categories.isEmpty() else category in it.categories) }
        val history = observations.list(rows.map { it.id })
        return StatisticsCalculator.calculate(rows, history, since, until, Instant.now(),
            source, company, category, groupBy, sources.definitions.associate { it.id to it.name }, companies.categories.associate { it.id to it.name })
    }
}
