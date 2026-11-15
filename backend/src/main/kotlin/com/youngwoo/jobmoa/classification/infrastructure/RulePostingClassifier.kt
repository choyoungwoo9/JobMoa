package com.youngwoo.jobmoa.classification.infrastructure

import com.youngwoo.jobmoa.classification.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.model.PostingUrl
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.text.Normalizer
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/** ID·회사명·URL·정답 라벨 없이 승인된 일반 규칙을 제목과 본문에 적용한다. */
@Component
class RulePostingClassifier : PostingClassifier {
    private val config = ClassPathResource("classification/rules-v0.5.json").inputStream.use { JsonMapper().readTree(it) }
    private val patterns = ConcurrentHashMap<String, Pattern>()
    private val tokens = config["shortTokens"].values().map { it.asString() }
    private val parser = config["parser"]
    private val order = config["ruleEvaluationOrder"].values().map { it.asString() }
    init {
        // 시작 시 모든 표현식/정규식을 확인한다. 잘못된 규칙으로 일부만 재분류하지 않는다.
        fun validate(node: JsonNode) {
            when {
                node.has("match") -> { require(node["match"]["field"].asString() in setOf("title", "skills", "duties", "position", "required")); regex(node["match"]["pattern"].asString()) }
                node.has("unit") -> listOf("all", "none").forEach { key -> node["unit"][key].forEach { regex(it.asString()) } }
                node.has("all") -> node["all"].forEach(::validate)
                node.has("any") -> node["any"].forEach(::validate)
                node.has("not") -> validate(node["not"])
                node.has("taggedAny") -> require(node["taggedAny"].all { it.asString() in order })
                else -> error("알 수 없는 분류 조건: $node")
            }
        }
        order.forEach { validate(config["rules"][it]["when"]) }
        listOf("dutiesStart", "positionStart", "requiredStart", "preferredStart", "contentStop").forEach { Pattern.compile(parser[it].asString()) }
    }
    private fun regex(expression: String): Pattern = patterns.computeIfAbsent(expression) {
        var guarded = it
        tokens.forEach { token ->
            guarded = Regex("(?<![A-Za-z0-9_])${Regex.escape(token)}(?![A-Za-z0-9_])").replace(guarded) { m -> "(?<![A-Za-z0-9])${m.value}(?![A-Za-z0-9])" }
        }
        Pattern.compile(guarded, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.UNICODE_CHARACTER_CLASS)
    }
    private fun matches(pattern: String, text: String) = regex(pattern).matcher(text).find()
    private fun canonical(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC).replace("\r\n", "\n").replace('\r', '\n')
    private fun heading(key: String, text: String): IntRange? {
        val m = Pattern.compile(parser[key].asString(), Pattern.UNICODE_CHARACTER_CLASS).matcher(text)
        return if (m.find()) m.start() until m.end() else null
    }
    private fun before(text: String, keys: List<String>): String = text.take(keys.mapNotNull { heading(it, text)?.first }.minOrNull() ?: text.length)
    private data class Context(val fields: Map<String, String>, val units: List<String>, val mode: String)
    private fun extract(title: String, body: String): Context {
        val text = canonical(body)
        val start = heading("dutiesStart", text); val pos = heading("positionStart", text); val req = heading("requiredStart", text)
        val ends = listOf("requiredStart", "preferredStart", "contentStop")
        val required = req?.let { before(text.substring(it.last + 1), listOf("preferredStart", "contentStop")) }.orEmpty()
        val position = pos?.let { before(text.substring(it.last + 1), listOf("dutiesStart") + ends) } ?: start?.let { text.take(it.first) }.orEmpty()
        val duties = before(text.substring((start ?: pos)?.let { it.last + 1 } ?: 0), ends)
        val lines = Regex("\\r\\n|[\\n\\r\\u000b\\u000c\\u001c-\\u001e\\u0085\\u2028\\u2029]")
        val units = lines.split(duties).map { it.replace(Regex("^\\s*[•·▪▶\\-]+\\s*"), "").trim() }.filter { line ->
            line.length >= 8 && !matches("채용 절차|커피챗|지원하기|전형 일정|면접|이력서|포트폴리오|우대사항|優遇|Nice to have", line) &&
                !matches("하지 않|하지않|않습니다|not responsible|필요에 따라|관심이 있|경험이 있으면|해본 경험|해 본 경험|개발 경험|운영 경험|경험이 있는 분|있으신 분|보신 분|가지신 분|필요시|필요하다면|필요하면|将来的|可能性があります|years? of|years? experience|Bachelor|Master|you have|experienced in|experience with|experience in|experience building", line)
        }.toMutableList()
        if (start != null) lines.split(before(text.take(start.first), ends)).filter {
            matches("successful candidate|this role|本ポジション|you will|社内向けのカスタムAIエージェント", it)
        }.forEach { units.add(it.trim()) }
        return Context(mapOf("title" to canonical(title), "skills" to req?.let { before(text.substring(it.last + 1), listOf("contentStop")) }.orEmpty(),
            "duties" to duties, "position" to position, "required" to required), units,
            if (start != null) "structured" else if (pos != null) "position-fallback" else "unstructured-fallback")
    }
    private fun unitMatches(unit: JsonNode, text: String) = unit["all"].all { matches(it.asString(), text) } && unit["none"].none { matches(it.asString(), text) }
    private fun check(expr: JsonNode, ctx: Context, accepted: Set<String>): Boolean = when {
        expr.has("match") -> matches(expr["match"]["pattern"].asString(), ctx.fields.getValue(expr["match"]["field"].asString()))
        expr.has("all") -> expr["all"].all { check(it, ctx, accepted) }
        expr.has("any") -> expr["any"].any { check(it, ctx, accepted) }
        expr.has("not") -> !check(expr["not"], ctx, accepted)
        expr.has("taggedAny") -> expr["taggedAny"].any { it.asString() in accepted }
        expr.has("unit") -> ctx.units.any { unitMatches(expr["unit"], it) }
        else -> error("알 수 없는 분류 조건")
    }
    override fun classify(title: String, body: String): ClassificationResult {
        val ctx = extract(title, body); val accepted = linkedSetOf<String>(); val evidence = linkedMapOf<String, List<String>>()
        order.forEach { category ->
            val expr = config["rules"][category]["when"]
            if (check(expr, ctx, accepted)) {
                accepted.add(category)
                val units = mutableListOf<String>()
                fun walk(node: JsonNode) {
                    if (!check(node, ctx, accepted)) return
                    if (node.has("unit")) units.addAll(ctx.units.filter { unitMatches(node["unit"], it) })
                    listOf("all", "any").forEach { op -> if (node.has(op)) node[op].forEach(::walk) }
                }
                walk(expr)
                evidence[category] = units.distinct().take(3).map { it.take(240) }
            }
        }
        if ("fullstack" in accepted) config["fullstackAdds"].forEach { accepted.add(it.asString()) }
        return ClassificationResult(accepted, Classification(config["version"].asString(), ctx.mode, evidence,
            PostingUrl.hash("$title\n$body"), Instant.now().toString(),
            if (accepted.isEmpty()) if (body.isBlank()) "저장된 본문이 없습니다." else "현재 규칙에서 담당 업무와 직군을 확정할 근거가 충분하지 않습니다." else null))
    }
}
