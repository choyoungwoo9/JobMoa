package com.youngwoo.jobmoa.collection.infrastructure.repository

import com.youngwoo.jobmoa.collection.domain.model.*
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Entity @Table(name = "collection_change")
class CollectionChangeEntity(@Id var taskId: String = "", var runId: String = "", var postingId: Long = 0,
    var outcome: String = "", var wasStarred: Boolean? = null, var becameClosed: Boolean? = null,
    var recordedAt: Instant = Instant.EPOCH)
interface CollectionChangeJpaRepository : JpaRepository<CollectionChangeEntity, String> {
    fun findAllByRunIdOrderByRecordedAtAscTaskIdAsc(runId: String): List<CollectionChangeEntity>
}
@Repository
class CollectionChangePersistence(private val jpa: CollectionChangeJpaRepository) : CollectionChangeRepository {
    override fun append(change: CollectionChange) { jpa.save(CollectionChangeEntity(change.taskId, change.runId, change.postingId, change.outcome, change.wasStarred, change.becameClosed, change.recordedAt)) }
    override fun list(runId: String) = jpa.findAllByRunIdOrderByRecordedAtAscTaskIdAsc(runId).map {
        CollectionChange(it.taskId, it.runId, it.postingId, it.outcome, it.wasStarred, it.becameClosed, it.recordedAt) }
}
