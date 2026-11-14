package com.youngwoo.jobmoa.posting.infrastructure.entity

import com.youngwoo.jobmoa.posting.domain.model.*
import jakarta.persistence.*
import java.time.Instant

@Entity @Table(name = "posting")
open class PostingEntity(
    @field:Id @field:GeneratedValue(strategy = GenerationType.IDENTITY) open var id: Long? = null,
    @field:Column(name = "source_id", nullable = false) open var sourceId: String = "",
    @field:Column(nullable = false) open var company: String = "",
    @field:Column(nullable = false, length = 1000) open var title: String = "",
    @field:Column(nullable = false) open var location: String = "",
    @field:Column(nullable = false, length = 2048) open var url: String = "",
    @field:Column(name = "url_hash", nullable = false, length = 64) open var urlHash: String = "",
    @field:Column(name = "first_seen", nullable = false) open var firstSeen: Instant = Instant.EPOCH,
    @field:Column(name = "last_seen", nullable = false) open var lastSeen: Instant = Instant.EPOCH,
    @field:Column(name = "is_read", nullable = false) open var read: Boolean = false,
    @field:Column(nullable = false) open var closed: Boolean = false,
    @field:Column(name = "last_change") open var lastChange: String? = null,
) {
    fun domain() = Posting(id!!, sourceId, company, title, location, url, urlHash, firstSeen, lastSeen, read, closed, lastChange)
}
fun Posting.entity() = PostingEntity(id.takeIf { it != 0L }, sourceId, company, title, location, url, urlHash, firstSeen, lastSeen, read, closed, lastChange)

@Entity @Table(name = "posting_version")
open class PostingVersionEntity(
    @field:Id @field:GeneratedValue(strategy = GenerationType.IDENTITY) open var id: Long? = null,
    @field:Column(name = "posting_id", nullable = false) open var postingId: Long = 0,
    @field:Column(name = "task_id", nullable = false) open var taskId: String = "",
    @field:Column(name = "content_hash", nullable = false, length = 64) open var contentHash: String = "",
    @field:Column(name = "body_text", nullable = false, columnDefinition = "longtext") open var bodyText: String = "",
    @field:Column(name = "capture_path", nullable = false, length = 500) open var capturePath: String = "",
    @field:Column(name = "saved_at", nullable = false) open var savedAt: Instant = Instant.EPOCH,
) {
    fun domain() = PostingVersion(id!!, postingId, taskId, contentHash, bodyText, capturePath, savedAt)
}
fun PostingVersion.entity() = PostingVersionEntity(id.takeIf { it != 0L }, postingId, taskId, contentHash, bodyText, capturePath, savedAt)
