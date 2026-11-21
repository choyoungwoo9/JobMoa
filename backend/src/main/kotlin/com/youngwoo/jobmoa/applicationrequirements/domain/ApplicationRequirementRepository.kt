package com.youngwoo.jobmoa.applicationrequirements.domain

import java.time.Duration
import java.time.Instant

interface ApplicationRequirementRepository {
    fun find(id: String): ApplicationRequirement?
    fun findByPosting(postingId: Long): List<ApplicationRequirement>
    fun lock(id: String): ApplicationRequirement?
    fun save(requirement: ApplicationRequirement)
    fun claim(now: Instant, lease: Duration): ApplicationRequirement?
    fun renew(id: String, token: String, now: Instant, lease: Duration): Boolean
}
