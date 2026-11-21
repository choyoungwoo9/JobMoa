package com.youngwoo.jobmoa.career.infrastructure.document

import com.youngwoo.jobmoa.career.application.CareerDocumentTextExtractor
import com.youngwoo.jobmoa.career.application.ExtractedDocumentText
import com.youngwoo.jobmoa.career.application.ExtractedTextFragment
import org.apache.tika.detect.DefaultDetector
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.parser.Parser
import org.apache.tika.parser.ocr.TesseractOCRConfig
import org.springframework.stereotype.Component
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.nio.file.Path

@Component
class TikaCareerDocumentTextExtractor : CareerDocumentTextExtractor {
    override fun extract(path: Path, originalFilename: String): ExtractedDocumentText {
        val metadata = Metadata().apply { set(TikaCoreProperties.RESOURCE_NAME_KEY, originalFilename) }
        val detector = DefaultDetector()
        val mediaType = TikaInputStream.get(path, metadata).use { input -> detector.detect(input, metadata, ParseContext()).toString() }
        val parser = AutoDetectParser()
        val handler = CareerTextHandler(MAX_CHARS_PER_FILE)
        val context = ParseContext().apply {
            set(Parser::class.java, parser)
            set(TesseractOCRConfig::class.java, TesseractOCRConfig().apply {
                setLanguage("kor+eng")
                setTimeoutMillis(OCR_TIMEOUT_MILLIS)
                setTrustedPageSeparator("\u000c")
            })
        }
        TikaInputStream.get(path, metadata).use { input -> parser.parse(input, handler, metadata, context) }
        return ExtractedDocumentText(mediaType, handler.fragments(), handler.truncated)
    }

    private class CareerTextHandler(private val maximumCharacters: Int) : DefaultHandler() {
        private val result = mutableListOf<ExtractedTextFragment>()
        private val buffer = StringBuilder()
        private var page = 0
        private var paragraph = 0
        private var heading = ""
        private var headingDepth = 0
        private var charactersSeen = 0
        var truncated: Boolean = false
            private set

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val name = localName.orEmpty().ifBlank { qName.orEmpty() }.lowercase()
            if (name == "div" && attributes.getValue("class")?.contains("page", ignoreCase = true) == true) {
                flush()
                page += 1
                paragraph = 0
            }
            if (name in BLOCK_ELEMENTS) flush()
            if (name in HEADING_ELEMENTS) headingDepth += 1
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (length <= 0) return
            if (charactersSeen >= maximumCharacters) {
                truncated = true
                return
            }
            val accepted = minOf(length, maximumCharacters - charactersSeen)
            buffer.append(ch, start, accepted)
            charactersSeen += accepted
            if (accepted < length) truncated = true
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = localName.orEmpty().ifBlank { qName.orEmpty() }.lowercase()
            if (name in HEADING_ELEMENTS) {
                val content = buffer.toString().replace(WHITESPACE, " ").trim()
                buffer.setLength(0)
                if (content.isNotBlank()) heading = content.take(180)
                headingDepth = (headingDepth - 1).coerceAtLeast(0)
            } else if (name in BLOCK_ELEMENTS) {
                flush()
            }
        }

        fun fragments(): List<ExtractedTextFragment> {
            flush()
            return result
        }

        private fun flush() {
            val content = buffer.toString().replace(WHITESPACE, " ").trim()
            buffer.setLength(0)
            if (content.isBlank()) return
            if (headingDepth > 0) {
                heading = content.take(180)
                return
            }
            paragraph += 1
            val location = buildString {
                if (page > 0) append("페이지 $page · ")
                if (heading.isNotBlank()) append("${heading.take(90)} · ")
                append("문단 $paragraph")
            }
            result += ExtractedTextFragment(result.size + 1, location, content)
        }

        companion object {
            private val BLOCK_ELEMENTS = setOf("p", "li", "td", "th", "tr", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre")
            private val HEADING_ELEMENTS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
            private val WHITESPACE = Regex("\\s+")
        }
    }

    companion object {
        private const val MAX_CHARS_PER_FILE = 4_000_000
        private const val OCR_TIMEOUT_MILLIS = 120_000L
    }
}
