package com.youngwoo.jobmoa.career.assistant.domain.repository

import com.youngwoo.jobmoa.career.assistant.domain.model.CareerAssistantTask
import java.time.Duration
import java.time.Instant

interface CareerAssistantTaskRepository {
    fun find(id: String): CareerAssistantTask?
    fun lock(id: String): CareerAssistantTask?
    fun save(task: CareerAssistantTask): CareerAssistantTask
    fun recent(type: String, limit: Int): List<CareerAssistantTask>
    fun drafts(parentTaskId: String): List<CareerAssistantTask>
    fun draft(parentTaskId: String, postingId: Long): CareerAssistantTask?
    fun claimNext(now: Instant, lease: Duration): CareerAssistantTask?
    fun renew(id: String, token: String, now: Instant, lease: Duration): Boolean
}
