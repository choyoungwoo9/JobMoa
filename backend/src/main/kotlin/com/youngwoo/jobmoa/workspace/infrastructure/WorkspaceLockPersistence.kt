package com.youngwoo.jobmoa.workspace.infrastructure

import com.youngwoo.jobmoa.collection.infrastructure.repository.CollectionLockJpaRepository
import com.youngwoo.jobmoa.workspace.domain.WorkspaceLock
import org.springframework.stereotype.Repository

@Repository
class WorkspaceLockPersistence(private val locks: CollectionLockJpaRepository) : WorkspaceLock {
    override fun shared() { checkNotNull(locks.shared("collection")) }
    override fun exclusive() = checkNotNull(locks.lockById("collection")).activeRunId != null
}
