package com.youngwoo.jobmoa.classification.application

import com.youngwoo.jobmoa.classification.domain.model.PostingClassifier
import com.youngwoo.jobmoa.posting.domain.repository.PostingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 공고 하나의 잠금 안에서 최신 본문으로 분류한다. 브라우저·외부 요청은 수행하지 않는다. */
@Service
class ReclassificationWriter(private val postings: PostingRepository, private val classifier: PostingClassifier) {
    @Transactional
    fun reclassify(id: Long): Boolean {
        val posting = postings.lock(id) ?: return false
        val body = postings.latestVersion(id)?.bodyText.orEmpty()
        val result = classifier.classify(posting.title, body)
        postings.save(posting.copy(categories = result.categories, classification = result.details))
        return true
    }
}

@Service
class ReclassificationService(private val postings: PostingRepository, private val writer: ReclassificationWriter) {
    @Synchronized
    fun rebuild(): Map<String, Any> {
        val ids = postings.list().map { it.id }
        val failed = mutableListOf<Long>()
        var completed = 0
        ids.forEach { id ->
            try { if (writer.reclassify(id)) completed++ } catch (e: Exception) {
                org.slf4j.LoggerFactory.getLogger(javaClass).error("공고 {} 재분류 실패", id, e)
                failed.add(id)
            }
        }
        val current = postings.list()
        return mapOf("requested" to ids.size, "completed" to completed, "failedPostingIds" to failed,
            "unclassified" to current.count { it.categories.isEmpty() }, "assignments" to current.sumOf { it.categories.size })
    }
}
