package com.youngwoo.jobmoa.crawler.infrastructure.site

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import org.springframework.stereotype.Component
import java.net.URI
import java.time.*
import java.time.format.DateTimeFormatter

/** NAVER Careers 통합 포털에 게시되는 계열사를 함께 다룬다. 판별 규칙도 이 수집기 소유다. */
@Component
class NaverRecruitmentSite : RecruitmentSite {
    override val definition = SourceDefinition("naver", "네이버", "https://recruit.navercorp.com/rcrt/list.do")

    override fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) {
        var offset = 0
        val seen = mutableSetOf<String>()
        while (true) {
            browser.navigate("https://recruit.navercorp.com/rcrt/loadJobList.do?firstIndex=$offset&recordCountPerPage=100&lang=ko")
            val response = browser.evaluate("() => JSON.parse(document.body.innerText)") as? Map<*, *> ?: error("네이버 목록 응답 형식이 바뀌었습니다.")
            check(response["result"] == "Y") { "네이버 목록 조회 실패: ${response["result"]}" }
            val rows = response["list"] as? List<*> ?: error("네이버 공고 목록이 없습니다.")
            val total = response["totalSize"].toString().toIntOrNull() ?: error("네이버 전체 공고 수를 확인하지 못했습니다.")
            if (rows.isEmpty()) {
                check(offset >= total) { "전체 $total 건 중 $offset 건만 조회됐습니다." }
                return
            }
            var fresh = 0
            rows.forEach { value ->
                val row = value as? Map<*, *> ?: error("네이버 공고 항목 형식이 바뀌었습니다.")
                val id = row["annoId"]?.toString() ?: error("공고 ID가 없습니다.")
                if (!seen.add(id)) return@forEach
                fresh++
                val title = row["annoSubject"]?.toString().orEmpty()
                if (!NaverDeveloperRule.accepts(row["classCdNm"]?.toString().orEmpty(), row["subJobCdNm"]?.toString().orEmpty(), title)) return@forEach
                val url = row["jobDetailLink"]?.toString() ?: error("공고 URL이 없습니다: $id")
                check(acceptsUrl(url)) { "미지원 네이버 상세 주소: $url" }
                emit(DiscoveredPosting(url, row["sysCompanyCdNm"]?.toString().orEmpty(), title))
            }
            offset += rows.size
            if (offset >= total) return
            check(fresh > 0 && offset < 100_000) { "네이버 목록 페이지가 반복되거나 비정상적으로 많습니다." }
        }
    }

    override fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        browser.waitFor(".card_title")
        browser.waitFor(".detail_wrap")
        val title = browser.text(".card_title").orEmpty()
        val info = (browser.evaluate("() => Array.from(document.querySelectorAll('.card_info dd')).map(e => e.innerText.trim())") as? List<*>)?.map { it.toString() }.orEmpty()
        check(info.size >= 3) { "네이버 공고의 직군·직무 정보를 확인하지 못했습니다." }
        if (!NaverDeveloperRule.accepts(info[1], info[2], title)) throw PostingExcluded("네이버 직군·직무 규칙에서 제외: ${info[1]} / ${info[2]} / $title")
        val body = browser.text(".detail_wrap").orEmpty().replace('\uFEFF', ' ').trim()
        val location = Regex("근무(?:지|장소)\\s*[:：]\\s*([^\\n]+)").find(body)?.groupValues?.get(1)?.trim()
            ?: posting.location
        val deadlineText = info.lastOrNull().orEmpty()
        val deadline = Regex("~\\s*(\\d{4}\\.\\d{2}\\.\\d{2})\\s*\\((\\d{2}:\\d{2})\\)").find(deadlineText)?.let { match ->
            LocalDateTime.parse("${match.groupValues[1]} ${match.groupValues[2]}", DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
                .atZone(ZoneId.of("Asia/Seoul")).toInstant()
        }
        val explicitClosed = browser.text(".link_box")?.contains("마감") == true || deadline?.isBefore(Instant.now()) == true
        return ExtractedPosting(title, info[0].ifBlank { posting.company }, location, body, explicitClosed)
    }

    override fun acceptsUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "recruit.navercorp.com" && uri.path == "/rcrt/view.do" &&
            Regex("(?:^|&)annoId=\\d+(?:&|$)").containsMatchIn(uri.rawQuery.orEmpty())
    }.getOrDefault(false)
}

private object NaverDeveloperRule {
    private val softwareRoles = setOf("Embedded SW", "Back-end", "Front-end", "Android", "iOS", "Software Development", "DevOps", "SRE")
    private val softwareTitle = Regex("software engineer|firmware engineer|backend|back.end|frontend|front.end|full.?stack|server (developer|engineer)|백엔드|프론트엔드|개발자|개발.?엔지니어", RegexOption.IGNORE_CASE)
    private val excluded = Regex("hardware|mechanical|보안|security|QA|test engineer|scientist|research engineer|기획|디자이너|사업|sales|contents developer", RegexOption.IGNORE_CASE)
    fun accepts(category: String, role: String, title: String): Boolean =
        category == "Tech" && !excluded.containsMatchIn(title) && (role in softwareRoles || softwareTitle.containsMatchIn(title))
}
