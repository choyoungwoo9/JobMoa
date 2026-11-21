package com.youngwoo.jobmoa.crawler.domain.model

/** 사이트 어댑터가 공고에 결부된 명시적인 접수 종료 증거를 확인한 경우에만 사용한다. */
class PostingClosed(val reason: String) : RuntimeException(reason)

/** HTTP 오류를 마감으로 간주할지는 사이트 어댑터가 판단한다. */
class BrowserHttpFailure(val status: Int, val url: String, val responseBody: String = "", kind: String = "페이지") :
    IllegalStateException("$kind 응답 오류: $status $url")
