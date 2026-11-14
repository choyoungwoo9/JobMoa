package com.youngwoo.jobmoa.crawler.domain.model

import java.net.URI
import java.security.MessageDigest

object PostingUrl {
    fun normalize(value: String): String {
        val uri = URI(value.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && (uri.port == -1 || uri.port == 443)) { "공식 HTTPS 공고 주소가 필요합니다." }
        val query = uri.rawQuery?.split('&')?.filterNot {
            val key = it.substringBefore('=').lowercase()
            key.startsWith("utm_") || key in setOf("fbclid", "gclid")
        }?.sorted()?.joinToString("&")?.takeIf { it.isNotBlank() }
        return "https://${uri.host.lowercase()}${uri.rawPath.ifBlank { "/" }}${query?.let { "?$it" } ?: ""}"
    }
    fun siteKey(url: String): String = URI(url).host.lowercase()
    fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

object DeveloperPostingRule {
    private val excluded = Regex("QA|test engineer|tester|scientist|data analyst|engineering manager|디자이너|기획|분석가|사업.?개발|영업|sales|채용 담당", RegexOption.IGNORE_CASE)
    private val included = Regex("backend|back.end|frontend|front.end|full.?stack|software engineer|server (developer|engineer)|data engineer|machine learning engineer|ML engineer|SRE|DevOps|백엔드|프론트엔드|풀스택|데이터.?엔지니어|개발자|개발.?엔지니어|개발(?=\\s*[(（/-]|$)|android|iOS", RegexOption.IGNORE_CASE)
    fun accepts(title: String): Boolean = title.isNotBlank() && !excluded.containsMatchIn(title) && included.containsMatchIn(title)
}
