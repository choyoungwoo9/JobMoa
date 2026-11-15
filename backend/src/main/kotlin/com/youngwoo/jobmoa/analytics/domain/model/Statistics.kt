package com.youngwoo.jobmoa.analytics.domain.model

import java.time.*

data class StatisticsPosting(
    val id: Long, val sourceId: String, val companyId: String, val companyName: String,
    val categories: Set<String>, val firstSeen: Instant, val lastSeen: Instant, val closed: Boolean,
)
data class StatisticsSummary(
    val total: Int, val open: Int, val closed: Int, val unclassified: Int, val staleOpen: Int,
    val discovered: Int, val closures: Int, val reopened: Int,
    val averageOpenAgeDays: Double?, val averageClosedDurationDays: Double?,
    val medianClosedDurationDays: Double?, val durationSamples: Int, val averageClosureGapDays: Double?,
)
data class StatisticsPoint(val date: LocalDate, val discovered: Int, val closures: Int, val reopened: Int, val observations: Int)
data class StatisticsGroup(val id: String, val name: String, val total: Int, val open: Int, val closed: Int,
    val discovered: Int, val closures: Int, val averageClosedDurationDays: Double?, val durationSamples: Int)
data class AgeBucket(val label: String, val count: Int)
data class StatisticsReport(
    val asOf: Instant, val from: LocalDate, val to: LocalDate, val timezone: String,
    val sourceId: String?, val companyId: String?, val categoryId: String?, val groupBy: String,
    val lastObservedAt: Instant?, val historyStartedAt: Instant?, val observedDays: Int,
    val summary: StatisticsSummary, val trend: List<StatisticsPoint>, val groups: List<StatisticsGroup>, val ages: List<AgeBucket>,
)

/** 관측한 상태 전이만 집계한다. 최초 마감 표본·열려 있는 미완료 기간은 완료 평균에서 제외한다. */
object StatisticsCalculator {
    private val zone = ZoneId.of("Asia/Seoul")
    private data class Closure(val postingId: Long, val at: Instant, val days: Double, val gap: Double)
    fun calculate(postings: List<StatisticsPosting>, observations: List<PostingObservation>, from: LocalDate, to: LocalDate,
        asOf: Instant, sourceId: String?, companyId: String?, categoryId: String?, groupBy: String,
        sourceNames: Map<String, String>, categoryNames: Map<String, String>): StatisticsReport {
        val start = from.atStartOfDay(zone).toInstant()
        val end = minOf(to.plusDays(1).atStartOfDay(zone).toInstant(), asOf.plusNanos(1))
        fun inPeriod(at: Instant) = at >= start && at < end
        fun day(at: Instant) = at.atZone(zone).toLocalDate()
        fun days(a: Instant, b: Instant) = Duration.between(a, b).toMillis().coerceAtLeast(0) / 86_400_000.0
        fun average(values: List<Double>) = values.takeIf { it.isNotEmpty() }?.average()
        val ids = postings.map { it.id }.toSet()
        val history = observations.filter { it.postingId in ids && it.observedAt <= asOf }
        val closures = mutableListOf<Closure>()
        val reopened = mutableListOf<Pair<Long, Instant>>()
        history.groupBy { it.postingId }.forEach { (id, rows) ->
            var closed: Boolean? = null
            var openedAt: Instant? = null
            var lastOpen: Instant? = null
            rows.sortedWith(compareBy<PostingObservation> { it.observedAt }.thenBy { it.id }).forEach { row ->
                if (closed == false && row.closed && openedAt != null) {
                    closures += Closure(id, row.observedAt, days(openedAt, row.observedAt), days(lastOpen!!, row.observedAt))
                    openedAt = null
                }
                if (!row.closed && closed != false) {
                    if (closed == true) reopened += id to row.observedAt
                    openedAt = row.observedAt
                }
                if (!row.closed) lastOpen = row.observedAt
                closed = row.closed
            }
        }
        val completed = closures.filter { inPeriod(it.at) }
        val durationValues = completed.map { it.days }.sorted()
        val median = durationValues.takeIf { it.isNotEmpty() }?.let { values ->
            if (values.size % 2 == 1) values[values.size / 2] else (values[values.size / 2 - 1] + values[values.size / 2]) / 2
        }
        val open = postings.filterNot { it.closed }
        val discoveries = postings.filter { inPeriod(it.firstSeen) }
        val periodReopened = reopened.filter { inPeriod(it.second) }
        val measurements = history.filter { !it.baseline && inPeriod(it.observedAt) }
        val trend = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.map { date ->
            StatisticsPoint(date, discoveries.count { day(it.firstSeen) == date }, completed.count { day(it.at) == date },
                periodReopened.count { day(it.second) == date }, measurements.count { day(it.observedAt) == date })
        }.toList()
        val grouped = postings.flatMap { posting ->
            val keys = when (groupBy) {
                "source" -> listOf(posting.sourceId)
                "company" -> listOf(posting.companyId)
                else -> posting.categories.ifEmpty { setOf("unclassified") }.toList()
            }
            keys.map { it to posting }
        }.groupBy({ it.first }, { it.second })
        val groups = grouped.map { (id, rows) ->
            val members = rows.map { it.id }.toSet()
            val samples = completed.filter { it.postingId in members }
            val name = when (groupBy) {
                "source" -> sourceNames[id] ?: id
                "company" -> rows.first().companyName
                else -> if (id == "unclassified") "분야 미분류" else categoryNames[id] ?: id
            }
            StatisticsGroup(id, name, rows.size, rows.count { !it.closed }, rows.count { it.closed },
                rows.count { inPeriod(it.firstSeen) }, samples.size, average(samples.map { it.days }), samples.size)
        }.sortedWith(compareByDescending<StatisticsGroup> { it.open }.thenByDescending { it.discovered }.thenBy { it.name })
        val ages = open.map { days(it.firstSeen, asOf) }
        return StatisticsReport(asOf, from, to, zone.id, sourceId, companyId, categoryId, groupBy,
            history.maxOfOrNull { it.observedAt }, history.minOfOrNull { it.observedAt },
            measurements.map { day(it.observedAt) }.distinct().size,
            StatisticsSummary(postings.size, open.size, postings.size - open.size, postings.count { it.categories.isEmpty() },
                open.count { it.lastSeen < asOf.minus(Duration.ofDays(7)) }, discoveries.size, completed.size, periodReopened.size,
                average(ages), average(durationValues), median, durationValues.size, average(completed.map { it.gap })), trend, groups,
            listOf(AgeBucket("7일 미만", ages.count { it < 7 }), AgeBucket("7~30일", ages.count { it >= 7 && it < 30 }),
                AgeBucket("30~90일", ages.count { it >= 30 && it < 90 }), AgeBucket("90일 이상", ages.count { it >= 90 })))
    }
}
