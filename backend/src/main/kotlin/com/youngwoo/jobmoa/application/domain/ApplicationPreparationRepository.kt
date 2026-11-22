package com.youngwoo.jobmoa.application.domain

import java.time.Duration
import java.time.Instant

interface ApplicationPreparationRepository {
    fun findCase(id: String): ApplicationCase?
    fun lockCase(id: String): ApplicationCase?
    fun findCaseByPosting(postingId: Long): ApplicationCase?
    fun cases(): List<ApplicationCase>
    fun saveCase(value: ApplicationCase): ApplicationCase
    fun questions(applicationId: String): List<ApplicationQuestion>
    fun findQuestion(id: String): ApplicationQuestion?
    fun lockQuestion(id: String): ApplicationQuestion?
    fun saveQuestion(value: ApplicationQuestion): ApplicationQuestion
    fun deleteQuestion(id: String)
    fun draft(applicationId: String): ApplicationMaterialsDraft?
    fun saveDraft(value: ApplicationMaterialsDraft): ApplicationMaterialsDraft
    fun sessions(applicationId: String): List<ApplicationInterviewSession>
    fun session(id: String): ApplicationInterviewSession?
    fun lockSession(id: String): ApplicationInterviewSession?
    fun saveSession(value: ApplicationInterviewSession): ApplicationInterviewSession
    fun turns(sessionId: String): List<ApplicationInterviewTurn>
    fun findTurn(id: String): ApplicationInterviewTurn?
    fun lockTurn(id: String): ApplicationInterviewTurn?
    fun saveTurn(value: ApplicationInterviewTurn): ApplicationInterviewTurn
}

interface ApplicationAiTaskRepository {
    fun find(id: String): ApplicationAiTask?
    fun lock(id: String): ApplicationAiTask?
    fun latest(applicationId: String, type: ApplicationAiTaskType): ApplicationAiTask?
    fun recent(applicationId: String, limit: Int): List<ApplicationAiTask>
    fun save(task: ApplicationAiTask): ApplicationAiTask
    fun claimNext(now: Instant, lease: Duration): ApplicationAiTask?
    fun renew(id: String, token: String, now: Instant, lease: Duration): Boolean
}
