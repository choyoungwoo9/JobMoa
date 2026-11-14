package com.youngwoo.jobmoa.crawler.domain.model

data class DiscoveredPosting(val url: String, val company: String, val title: String, val location: String = "")
data class ExtractedPosting(val title: String, val company: String, val location: String, val bodyText: String, val closed: Boolean = false)
data class SourceDefinition(val id: String, val name: String, val listingUrl: String)
