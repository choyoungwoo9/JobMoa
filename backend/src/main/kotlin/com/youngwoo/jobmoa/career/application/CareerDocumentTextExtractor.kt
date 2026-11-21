package com.youngwoo.jobmoa.career.application

import java.nio.file.Path

data class ExtractedDocumentText(
    val mediaType: String,
    val fragments: List<ExtractedTextFragment>,
    val truncated: Boolean,
)

data class ExtractedTextFragment(val ordinal: Int, val locationLabel: String, val content: String)

interface CareerDocumentTextExtractor {
    fun extract(path: Path, originalFilename: String): ExtractedDocumentText
}
