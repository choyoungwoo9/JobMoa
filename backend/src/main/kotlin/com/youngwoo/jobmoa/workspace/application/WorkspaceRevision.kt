package com.youngwoo.jobmoa.workspace.application

import com.youngwoo.jobmoa.workspace.domain.WorkspaceSnapshotRepository
import org.springframework.stereotype.Component

@Component
class WorkspaceRevision(private val repository: WorkspaceSnapshotRepository) {
    fun current() = repository.revision()
}
