package com.youngwoo.jobmoa.posting.domain.model

import java.time.Instant

data class VersionSummary(val id: Long, val postingId: Long, val savedAt: Instant, val capturePath: String)

data class PostingVersion(
    val id: Long = 0,
    val postingId: Long,
    val taskId: String,
    val contentHash: String,
    val bodyText: String,
    val capturePath: String,
    val savedAt: Instant,
)

data class Posting(
    val id: Long = 0,
    val sourceId: String,
    val company: String,
    val title: String,
    val location: String,
    val url: String,
    val urlHash: String,
    val firstSeen: Instant,
    val lastSeen: Instant,
    val read: Boolean = false,
    val closed: Boolean = false,
    val lastChange: String? = null,
    val categories: Set<String> = emptySet(),
    val classification: com.youngwoo.jobmoa.classification.domain.model.Classification? = null,
)
