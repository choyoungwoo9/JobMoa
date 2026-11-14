package com.youngwoo.jobmoa.crawler.domain.model

/** 회사와 포털은 별개다. 한 통합 포털에 여러 회사가 공고를 게시할 수 있다. */
data class CompanyDefinition(
    val id: String,
    val sourceId: String,
    val name: String,
    val listingUrl: String,
    val portalId: String,
    val companyNames: Set<String>,
    val supportedCategoryIds: Set<String>,
)

data class CategoryDefinition(val id: String, val name: String, val description: String)
