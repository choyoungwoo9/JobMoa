package com.youngwoo.jobmoa.aiqa.application

import com.youngwoo.jobmoa.aiqa.domain.model.*
import com.youngwoo.jobmoa.aiqa.domain.repository.AiChatRepository
import com.youngwoo.jobmoa.crawler.application.CompanyRegistry
import org.springframework.stereotype.Service

@Service
class AiKnowledgeService(
    private val repository: AiChatRepository,
    private val companies: CompanyRegistry,
) {
    private val categories = companies.categories.filter { it.id != "all" }.associate { it.id to it.name }
    private val stopWords = setOf(
        "공고", "채용", "회사", "기업", "어떤", "있는", "있어", "있나요", "찾아", "찾아줘", "보여", "보여줘",
        "알려", "알려줘", "대해", "중에서", "중", "전체", "모두", "전부", "현재", "관련", "지원", "가능", "가능한",
        "열려", "열린", "마감", "종료", "비교", "추천", "정리", "요약", "정보", "그리고", "이랑", "하고", "에서",
        "으로", "에게", "어떻게", "얼마나", "무엇", "뭐", "있을까", "궁금", "좀", "부탁", "해줘", "주세요",
    )
    private val openQuestion = Regex("열려\\s*있|열린\\s*공고|지원\\s*가능|현재\\s*진행\\s*중|접수\\s*중")
    private val closedQuestion = Regex("마감\\s*공고|종료\\s*공고|닫힌\\s*공고|마감된\\s*공고")
    private val recentQuestion = Regex("최근|최신|요즘|새로\\s*올라온|방금")

    fun context(question: String, history: List<AiMessage>): AiChatContext {
        val all = repository.postingIndex()
        val summary = summarize(all)
        val queryText = (history.takeLast(6).map { it.content } + question).joinToString(" ")
        val terms = terms(queryText)
        val currentMatches = when {
            openQuestion.containsMatchIn(question) -> all.filterNot { it.closed }
            closedQuestion.containsMatchIn(question) -> all.filter { it.closed }
            else -> all
        }
        val ranked = currentMatches.map { posting -> posting to score(posting, terms, queryText) }
            .sortedWith(compareByDescending<Pair<AiPostingContext, Int>> { it.second }.thenByDescending { it.first.firstSeen })
        val matching = ranked.filter { it.second > 0 }.map { it.first }
        val candidates = when {
            matching.isNotEmpty() -> matching
            recentQuestion.containsMatchIn(question) -> currentMatches.sortedByDescending { it.firstSeen }
            openQuestion.containsMatchIn(question) || closedQuestion.containsMatchIn(question) -> currentMatches.sortedByDescending { it.firstSeen }
            question.contains("미분류") -> all.filter { it.categoryIds.isEmpty() }.sortedByDescending { it.firstSeen }
            else -> emptyList()
        }
        val selected = candidates.take(MAX_POSTINGS).map { posting ->
            posting.copy(body = excerpt(posting.body, terms))
        }
        return AiChatContext(summary, selected, candidates.size, candidates.size > selected.size, history)
    }

    fun citations(task: AiTask): List<AiPostingContext> = decodeContexts(task.postingContextJson)

    fun summarize(all: List<AiPostingContext>): AiWorkspaceSummary {
        fun counts(items: List<AiPostingContext>) = mapOf(
            "전체" to items.size,
            "열림" to items.count { !it.closed },
            "마감" to items.count { it.closed },
        )
        val byCompany = all.groupBy { it.company }.toSortedMap().mapValues { (_, rows) -> counts(rows) }
        val byCategory = categories.mapNotNull { (id, name) ->
            val rows = all.filter { id in it.categoryIds }
            if (rows.isEmpty()) null else name to counts(rows)
        }.toMap().toSortedMap()
        return AiWorkspaceSummary(all.size, all.count { !it.closed }, all.count { it.closed },
            all.count { it.categoryIds.isEmpty() }, byCompany, byCategory)
    }

    private fun score(posting: AiPostingContext, terms: List<String>, query: String): Int {
        val companyNames = companies.list().firstOrNull { it.name == posting.company }?.companyNames.orEmpty()
        val companyText = (listOf(posting.company) + companyNames).joinToString(" ").lowercase()
        val title = posting.title.lowercase()
        val location = posting.location.lowercase()
        val categoryText = posting.categoryIds.mapNotNull(categories::get).joinToString(" ").lowercase()
        val body = posting.body.lowercase()
        var result = 0
        if (query.trim().length >= 3 && companyText.contains(query.trim().lowercase())) result += 30
        if (query.trim().length >= 3 && title.contains(query.trim().lowercase())) result += 25
        for (term in terms) {
            when {
                companyText.contains(term) -> result += 16
                title.contains(term) -> result += 12
                categoryText.contains(term) -> result += 10
                location.contains(term) -> result += 6
                body.contains(term) -> result += 1
            }
        }
        return result
    }

    private fun terms(value: String): List<String> = Regex("[\\p{L}\\p{N}]{2,}")
        .findAll(value.lowercase())
        .map { it.value }
        .filter { it !in stopWords }
        .distinct()
        .toList()
        .takeLast(32)

    private fun excerpt(body: String, terms: List<String>): String {
        if (body.length <= MAX_BODY_CHARS) return body
        if (terms.isEmpty()) return body.take(MAX_BODY_CHARS)
        val normalized = body.lowercase()
        val windows = mutableListOf<IntRange>()
        for (term in terms) {
            var start = 0
            var found = 0
            while (found < 2) {
                val index = normalized.indexOf(term, start)
                if (index < 0) break
                windows.add((index - 260).coerceAtLeast(0)..(index + term.length + 500).coerceAtMost(body.length))
                start = index + term.length
                found++
            }
        }
        if (windows.isEmpty()) return body.take(MAX_BODY_CHARS)
        val merged = windows.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { result, next ->
            val last = result.lastOrNull()
            if (last != null && next.first <= last.last + 80) result[result.lastIndex] = last.first..maxOf(last.last, next.last)
            else result.add(next)
            result
        }
        return merged.asSequence().map { body.substring(it.first, it.last.coerceAtMost(body.lastIndex) + 1) }
            .joinToString("\n…\n")
            .take(MAX_BODY_CHARS)
    }

    private fun decodeContexts(json: String): List<AiPostingContext> {
        val root = tools.jackson.databind.json.JsonMapper().readTree(json)
        if (!root.isArray) return emptyList()
        return root.values().map { item ->
            AiPostingContext(
                item["id"].asLong(), item["sourceId"].asString(), item["company"].asString(),
                item["title"].asString(), item["location"].asString(), item["url"].asString(),
                java.time.Instant.parse(item["firstSeen"].asString()), item["closed"].asBoolean(),
                item["read"].asBoolean(), item["categoryIds"].values().map { it.asString() },
                item["versionId"].takeUnless { it.isNull }?.asLong(),
                item["versionSavedAt"].takeUnless { it.isNull }?.asString()?.let(java.time.Instant::parse),
                item["body"].asString(),
            )
        }
    }

    companion object {
        private const val MAX_POSTINGS = 12
        private const val MAX_BODY_CHARS = 6000
    }
}
