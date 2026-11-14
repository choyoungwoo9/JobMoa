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

