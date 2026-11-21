package com.youngwoo.jobmoa.career.assistant.domain.model

import java.time.Instant

enum class CareerAssistantTaskType { RECOMMENDATIONS, POSTING_DRAFT }
enum class CareerAssistantTaskStatus { WAITING_AI, ANALYZING, COMPLETED, FAILED }

data class CareerAssistantTask(
    val id: String,
    val type: CareerAssistantTaskType,
    val parentTaskId: String? = null,
    val postingId: Long? = null,
    val status: CareerAssistantTaskStatus,
    val requestJson: String,
    val resultJson: String? = null,
    val errorMessage: String? = null,
    val attempts: Int = 0,
    val leaseToken: String? = null,
    val leaseExpiresAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CareerAssistantTaskLease(val task: CareerAssistantTask, val token: String)

data class CareerRecommendationProposal(
    val postingId: Long,
    val fit: String,
    val explanation: String,
    val matchingPoints: List<String> = emptyList(),
    val gaps: List<String> = emptyList(),
    val verifiedFactIds: List<String> = emptyList(),
)

data class CareerRecommendationProposalResult(
    val preferenceSummary: String = "",
    val recommendations: List<CareerRecommendationProposal> = emptyList(),
)

data class CareerDraftSectionProposal(
    val heading: String,
    val content: String,
    val verifiedFactIds: List<String> = emptyList(),
)

data class CareerPortfolioProjectProposal(
    val title: String,
    val selectionReason: String,
    val summary: String,
    val verifiedFactIds: List<String> = emptyList(),
)

data class CareerPostingDraftProposal(
    val resumeHeadline: String,
    val resumeSummary: String,
    val resumeHeadlineFactIds: List<String> = emptyList(),
    val resumeSummaryFactIds: List<String> = emptyList(),
    val resumeSections: List<CareerDraftSectionProposal> = emptyList(),
    val portfolioProjects: List<CareerPortfolioProjectProposal> = emptyList(),
    val informationGaps: List<String> = emptyList(),
)

data class CareerFactEvidenceView(val locationLabel: String, val excerpt: String)
data class CareerFactView(val id: String, val title: String, val evidence: List<CareerFactEvidenceView>)

data class CareerRecommendationPosting(
    val id: Long,
    val company: String,
    val title: String,
    val location: String,
    val url: String,
    val categories: List<String>,
    val firstSeen: Instant,
)

data class CareerRecommendationItem(
    val posting: CareerRecommendationPosting,
    val fit: String,
    val explanation: String,
    val matchingPoints: List<String>,
    val gaps: List<String>,
    val evidence: List<CareerFactView>,
)

data class CareerRecommendationCriteria(
    val preferenceText: String,
    val categoryNames: List<String>,
    val companyNames: List<String>,
    val locationKeywords: String,
)

data class CareerRecommendationResult(
    val criteria: CareerRecommendationCriteria,
    val preferenceSummary: String,
    val recommendations: List<CareerRecommendationItem>,
)

data class CareerDraftSection(
    val heading: String,
    val content: String,
    val evidence: List<CareerFactView>,
)

data class CareerPortfolioProject(
    val title: String,
    val selectionReason: String,
    val summary: String,
    val evidence: List<CareerFactView>,
)

data class CareerPostingDraftResult(
    val posting: CareerRecommendationPosting,
    val resumeHeadline: String,
    val resumeHeadlineEvidence: List<CareerFactView>,
    val resumeSummary: String,
    val resumeSummaryEvidence: List<CareerFactView>,
    val resumeSections: List<CareerDraftSection>,
    val portfolioProjects: List<CareerPortfolioProject>,
    val informationGaps: List<String>,
)
