package com.youngwoo.jobmoa.posting.domain.repository

import com.youngwoo.jobmoa.posting.domain.model.Posting
import com.youngwoo.jobmoa.posting.domain.model.PostingVersion

interface PostingRepository {
    fun findByHashForUpdate(hash: String): Posting?
    fun find(id: Long): Posting?
    fun list(): List<Posting>
    fun save(posting: Posting): Posting
    fun versions(id: Long): List<PostingVersion>
    fun saveVersion(version: PostingVersion): PostingVersion
    fun markRead(id: Long, read: Boolean)
}
