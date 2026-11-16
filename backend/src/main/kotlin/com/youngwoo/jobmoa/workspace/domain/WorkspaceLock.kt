package com.youngwoo.jobmoa.workspace.domain

interface WorkspaceLock {
    fun shared()
    /** 배타 잠금을 획득한 뒤 수집 중인지 반환한다. */
    fun exclusive(): Boolean
}
