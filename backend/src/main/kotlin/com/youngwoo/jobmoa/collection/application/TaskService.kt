package com.youngwoo.jobmoa.collection.application

import com.youngwoo.jobmoa.analytics.domain.model.PostingObservation
import com.youngwoo.jobmoa.analytics.domain.repository.ObservationRepository
import com.youngwoo.jobmoa.applicationrequirements.application.ApplicationRequirementService
import com.youngwoo.jobmoa.collection.domain.model.*
import com.youngwoo.jobmoa.collection.domain.repository.CrawlTaskRepository
import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.posting.domain.model.*
import com.youngwoo.jobmoa.posting.domain.repository.PostingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class TaskService(private val tasks: CrawlTaskRepository, private val postings: PostingRepository, private val observations: ObservationRepository, private val classifier: com.youngwoo.jobmoa.classification.domain.model.PostingClassifier, private val changes: CollectionChangeRepository, private val interests: com.youngwoo.jobmoa.interest.domain.repository.PostingInterestRepository, private val applicationRequirements: ApplicationRequirementService) {
    @Transactional
    fun body(id: String, token: String, body: ExtractedPosting): CrawlTask {
        val task = tasks.lock(id).also { it.requireOwnership(token, Instant.now()) }
        check(task.stage == CrawlStage.BODY) { "본문 추출 단계가 아닙니다." }
        require(body.title.isNotBlank() && body.bodyText.length >= 40) { "공고 본문을 충분히 추출하지 못했습니다." }
        val normalized = listOf(body.company, body.title, body.location, body.bodyText.replace(Regex("\\s+"), " ").trim()).joinToString("\n")
        val contentHash = PostingUrl.hash(normalized)
        // 이전 버전은 마감 상태까지 해시했다. 과거 해시는 유지하면서 상태만 바뀐 본문도 재사용한다.
        val compatibleHashes = setOf(contentHash, PostingUrl.hash("$normalized\ntrue"), PostingUrl.hash("$normalized\nfalse"))
        val old = postings.findByHashForUpdate(task.urlHash)
        val reusable = old?.let { postings.latestVersion(it.id) }?.takeIf { it.contentHash in compatibleHashes && it.capturePath.isNotBlank() }
        val next = task.copy(title = body.title.take(1000), company = body.company.take(255),
            location = body.location.take(255), bodyText = if (reusable == null) body.bodyText else null,
            contentHash = reusable?.contentHash ?: contentHash, closed = body.closed,
            capturePath = reusable?.capturePath, reusedVersionId = reusable?.id,
            stage = if (reusable == null) CrawlStage.CAPTURE else CrawlStage.SAVE)
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
        check(task.stage == CrawlStage.SAVE && task.contentHash != null && task.capturePath != null)
        val now = Instant.now()
        val old = postings.findByHashForUpdate(task.urlHash)
        val latest = old?.let { postings.latestVersion(it.id) }
        val reused = task.reusedVersionId?.let { versionId ->
            val version = checkNotNull(postings.findVersion(versionId)) { "재사용할 공고 버전을 찾을 수 없습니다." }
            check(old != null && version.postingId == old.id && version.contentHash == task.contentHash &&
                version.capturePath == task.capturePath && latest?.id == version.id) { "재사용할 공고 버전이 현재 본문과 일치하지 않습니다." }
            version
        }
        check(reused != null || task.bodyText != null) { "저장할 공고 본문이 없습니다." }
        val outcome = when {
            old == null -> TaskOutcome.NEW
            latest?.contentHash == task.contentHash -> TaskOutcome.IDENTICAL
            else -> TaskOutcome.CHANGED
        }
        val classification = classifier.classify(task.title, task.bodyText ?: checkNotNull(reused ?: latest).bodyText)
        val posting = postings.save(old?.copy(title = task.title, company = task.company, location = task.location,
            lastSeen = now, closed = task.closed, lastChange = outcome.name.lowercase(), categories = classification.categories, classification = classification.details) ?: Posting(
            sourceId = task.sourceId, company = task.company, title = task.title, location = task.location,
            url = task.url, urlHash = task.urlHash, firstSeen = task.discoveredAt, lastSeen = now,
            closed = task.closed, lastChange = "new", categories = classification.categories, classification = classification.details))
        applicationRequirements.collectAfterPostingCrawl(posting.id, task.runId)
        if (outcome != TaskOutcome.IDENTICAL) postings.saveVersion(PostingVersion(postingId = posting.id,
            taskId = task.id, contentHash = task.contentHash, bodyText = checkNotNull(task.bodyText), capturePath = task.capturePath, savedAt = now))
        changes.append(CollectionChange(task.id, task.runId, posting.id, outcome.name, interests.find(posting.id)?.starred ?: false,
            old != null && !old.closed && task.closed, now))
        observations.append(PostingObservation(postingId = posting.id, taskId = task.id, observedAt = now, closed = task.closed))
        tasks.save(task.copy(status = TaskStatus.COMPLETED, outcome = outcome, bodyText = null, leaseToken = null, leaseExpiresAt = null))
        resolveParents(task, now)
    }

    /** 마감 안내 화면을 본문·새 버전으로 저장하지 않고, 마지막 정상 본문과 개인 관리 정보를 보존한다. */
    @Transactional
    fun closeExisting(id: String, token: String, reason: String) {
        val task = tasks.lock(id).also { it.requireOwnership(token, Instant.now()) }
        require(reason.isNotBlank())
        val now = Instant.now()
        val old = postings.findByHashForUpdate(task.urlHash)
        if (old != null) {
            postings.save(old.copy(closed = true, lastSeen = now, lastChange = "closed"))
            changes.append(CollectionChange(task.id, task.runId, old.id, TaskOutcome.CLOSED.name,
                interests.find(old.id)?.starred ?: false, !old.closed, now))
            observations.append(PostingObservation(postingId = old.id, taskId = task.id, observedAt = now, closed = true))
        }
        tasks.save(task.copy(status = TaskStatus.COMPLETED, outcome = if (old == null) TaskOutcome.EXCLUDED else TaskOutcome.CLOSED,
            closed = true, error = "마감 확인: ${reason.take(1800)}" + if (old == null) " (기존 공고 없음, 신규 저장 제외)" else "",
            bodyText = null, leaseToken = null, leaseExpiresAt = null))
        resolveParents(task, now)
    }

    @Transactional
    fun exclude(id: String, token: String, reason: String) {
        val task = tasks.lock(id).also { it.requireOwnership(token, Instant.now()) }
        tasks.save(task.copy(status = TaskStatus.COMPLETED, outcome = TaskOutcome.EXCLUDED,
            error = reason, leaseToken = null, leaseExpiresAt = null))
        resolveParents(task, Instant.now())
    }

    private fun resolveParents(task: CrawlTask, now: Instant) {
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
