package com.youngwoo.jobmoa.workspace.application

import com.youngwoo.jobmoa.workspace.domain.WorkspaceLock
import com.youngwoo.jobmoa.shared.CollectionConflict
import org.springframework.stereotype.Component

/** 반드시 호출자의 트랜잭션 안에서 획득한다. 일반 쓰기와 유지보수의 잠금 순서를 통일한다. */
@Component
class WorkspaceAccess(private val locks: WorkspaceLock) {
    fun writing() = locks.shared()
    fun maintenance() {
        if (locks.exclusive()) throw CollectionConflict("수집이 끝난 뒤 데이터 관리 기능을 사용할 수 있습니다.")
    }
}
