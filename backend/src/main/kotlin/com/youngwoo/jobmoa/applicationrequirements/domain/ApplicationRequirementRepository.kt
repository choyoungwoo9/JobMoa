package com.youngwoo.jobmoa.applicationrequirements.domain

import java.time.Duration
import java.time.Instant

interface ApplicationRequirementRepository {
    fun find(id: String): ApplicationRequirement?
    fun findByPosting(postingId: Long): List<ApplicationRequirement>
    fun findByCollectionRunAndPosting(runId: String, postingId: Long): ApplicationRequirement?
    fun countByCollectionRun(runId: String): List<ApplicationRequirementStatusCount>
    fun summarizeCollectionRun(runId: String): List<ApplicationRequirementRunSummary>
    fun latestPublicSummary(): List<ApplicationRequirementRunSummary>
    fun lock(id: String): ApplicationRequirement?
    fun save(requirement: ApplicationRequirement)
    fun claim(now: Instant, lease: Duration): ApplicationRequirement?
    fun claimPublicPage(now: Instant, lease: Duration): ApplicationRequirement?
    fun renew(id: String, token: String, now: Instant, lease: Duration): Boolean
    fun renewPublicPage(id: String, token: String, now: Instant, lease: Duration): Boolean
}
