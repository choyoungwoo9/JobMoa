package com.youngwoo.jobmoa.classification.domain.model

data class Classification(
    val rulesVersion: String,
    val parserMode: String,
    val evidence: Map<String, List<String>>,
    val inputHash: String,
    val classifiedAt: String,
    val unclassifiedReason: String? = null,
)
data class ClassificationResult(val categories: Set<String>, val details: Classification)
interface PostingClassifier {
    fun classify(title: String, body: String): ClassificationResult
}
