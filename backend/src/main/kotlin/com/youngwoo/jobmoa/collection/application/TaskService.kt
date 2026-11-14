package com.youngwoo.jobmoa.collection.application

import com.youngwoo.jobmoa.collection.domain.model.*
import com.youngwoo.jobmoa.collection.domain.repository.CrawlTaskRepository
import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.posting.domain.model.*
import com.youngwoo.jobmoa.posting.domain.repository.PostingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class TaskService(private val tasks: CrawlTaskRepository, private val postings: PostingRepository) {
    @Transactional
    fun body(id: String, token: String, body: ExtractedPosting): CrawlTask {
        val task = tasks.lock(id).also { it.requireOwnership(token, Instant.now()) }
        check(task.stage == CrawlStage.BODY) { "본문 추출 단계가 아닙니다." }
        require(body.title.isNotBlank() && body.bodyText.length >= 40) { "공고 본문을 충분히 추출하지 못했습니다." }
        val normalized = listOf(body.company, body.title, body.location, body.bodyText.replace(Regex("\\s+"), " ").trim(), body.closed.toString()).joinToString("\n")
        val next = task.copy(title = body.title.take(1000), company = body.company.take(255),
            location = body.location.take(255), bodyText = body.bodyText, contentHash = PostingUrl.hash(normalized), closed = body.closed, stage = CrawlStage.CAPTURE)
        tasks.save(next)
        return next
    }

    @Transactional
    fun captured(id: String, token: String, path: String): CrawlTask {
        val task = tasks.lock(id).also { it.requireOwnership(token, Instant.now()) }
        check(task.stage == CrawlStage.CAPTURE) { "캡처 단계가 아닙니다." }
        require(path.isNotBlank() && path.length <= 500)
        val next = task.copy(capturePath = path, stage = CrawlStage.SAVE)
        tasks.save(next)
        return next
    }

    @Transactional
    fun finish(id: String, token: String) {
        val task = tasks.lock(id).also { it.requireOwnership(token, Instant.now()) }
        check(task.stage == CrawlStage.SAVE && task.bodyText != null && task.contentHash != null && task.capturePath != null)
        val now = Instant.now()
        val old = postings.findByHashForUpdate(task.urlHash)
        val latest = old?.let { postings.versions(it.id).firstOrNull() }
        val outcome = when {
            old == null -> TaskOutcome.NEW
            latest?.contentHash == task.contentHash -> TaskOutcome.IDENTICAL
            else -> TaskOutcome.CHANGED
        }
        val posting = postings.save(old?.copy(title = task.title, company = task.company, location = task.location,
            lastSeen = now, closed = task.closed, lastChange = outcome.name.lowercase()) ?: Posting(
            sourceId = task.sourceId, company = task.company, title = task.title, location = task.location,
            url = task.url, urlHash = task.urlHash, firstSeen = task.discoveredAt, lastSeen = now,
            closed = task.closed, lastChange = "new"))
        if (outcome != TaskOutcome.IDENTICAL) postings.saveVersion(PostingVersion(postingId = posting.id,
            taskId = task.id, contentHash = task.contentHash, bodyText = task.bodyText, capturePath = task.capturePath, savedAt = now))
        tasks.save(task.copy(status = TaskStatus.COMPLETED, outcome = outcome, leaseToken = null, leaseExpiresAt = null))
        var parentId = task.retryOfTaskId
        val visited = mutableSetOf<String>()
        while (parentId != null && visited.add(parentId)) {
            val parent = tasks.lock(parentId)
            tasks.save(parent.copy(resolvedAt = now))
            parentId = parent.retryOfTaskId
        }
    }

    @Transactional
    fun fail(id: String, token: String, error: Throwable) {
        val task = tasks.lock(id).also { it.requireOwnership(token, Instant.now()) }
        tasks.save(task.copy(status = TaskStatus.FAILED, error = error.stackTraceToString().take(24_000),
            failedAt = Instant.now(), leaseToken = null, leaseExpiresAt = null))
    }
}
