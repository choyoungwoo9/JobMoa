package com.youngwoo.jobmoa.collection.domain.repository

import com.youngwoo.jobmoa.collection.domain.model.*
import java.time.Instant

interface CollectionRepository {
    fun activeRunForUpdate(): CollectionRun?
    fun activate(run: CollectionRun, sources: List<RunSource>)
    fun findRun(id: String): CollectionRun?
    fun recentRuns(limit: Int = 30): List<CollectionRun>
    fun sources(runId: String): List<RunSource>
    fun nextDiscovery(): RunSource?
    fun recoverDiscovery(): Int
    fun saveSource(source: RunSource)
    fun finishIfReady(runId: String, now: Instant)
}
