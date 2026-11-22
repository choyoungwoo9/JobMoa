package com.youngwoo.jobmoa.interest.application

import com.youngwoo.jobmoa.interest.domain.model.*
import com.youngwoo.jobmoa.interest.domain.repository.PostingInterestRepository
import com.youngwoo.jobmoa.posting.domain.repository.PostingRepository
import com.youngwoo.jobmoa.shared.ResourceNotFound
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PostingInterestService(private val postings: PostingRepository, private val interests: PostingInterestRepository, private val access: com.youngwoo.jobmoa.workspace.application.WorkspaceAccess) {
    // 최초 생성과 기존 정보 수정을 공고 행 잠금으로 직렬화한다. 수집기는 개인 관리 테이블을 변경하지 않는다.
    private fun current(id: Long): PostingInterest {
        access.writing()
        postings.lock(id) ?: throw ResourceNotFound("공고를 찾을 수 없습니다.")
        return interests.find(id) ?: PostingInterest(id)
    }

    @Transactional
    fun star(id: Long, starred: Boolean): PostingInterest = interests.save(current(id).star(starred, Instant.now()))

    @Transactional
    fun manage(id: Long, note: String, priority: InterestPriority): PostingInterest =
        interests.save(current(id).manage(note, priority, Instant.now()))
}
