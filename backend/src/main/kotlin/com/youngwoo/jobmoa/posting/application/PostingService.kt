package com.youngwoo.jobmoa.posting.application

import com.youngwoo.jobmoa.posting.domain.repository.PostingRepository
import com.youngwoo.jobmoa.shared.ResourceNotFound
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PostingService(private val postings: PostingRepository, private val access: com.youngwoo.jobmoa.workspace.application.WorkspaceAccess) {
    @Transactional
    fun markRead(id: Long, read: Boolean) { access.writing(); postings.markRead(id, read) }
    @Transactional(readOnly = true)
    fun requirePosting(id: Long) = postings.find(id) ?: throw ResourceNotFound("공고를 찾을 수 없습니다.")
}
