package com.youngwoo.jobmoa.collection.domain.repository

import com.youngwoo.jobmoa.collection.domain.model.CrawlTask
import java.time.Duration
import java.time.Instant

interface CrawlTaskRepository {
    fun enqueue(task: CrawlTask): Boolean
    fun pendingSites(): List<String>
    fun claim(siteKey: String, duration: Duration): CrawlTask?
    fun find(id: String): CrawlTask?
    fun lock(id: String): CrawlTask
    fun save(task: CrawlTask)
    fun renew(id: String, token: String, duration: Duration): Boolean
    fun recoverExpired(now: Instant): Int
    fun tasks(runId: String): List<CrawlTask>
}
