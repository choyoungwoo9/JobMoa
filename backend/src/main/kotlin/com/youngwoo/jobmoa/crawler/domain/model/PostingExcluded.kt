package com.youngwoo.jobmoa.crawler.domain.model

/** 각 사이트 수집기가 자신의 판별 규칙에 따라 수집 대상에서 제외한다. */
class PostingExcluded(message: String) : RuntimeException(message)
