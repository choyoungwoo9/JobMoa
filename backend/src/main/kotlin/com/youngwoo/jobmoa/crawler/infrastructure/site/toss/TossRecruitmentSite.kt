package com.youngwoo.jobmoa.crawler.infrastructure.site.toss

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.*

/** 토스 통합 포털의 묶음 공고를 실제 계열사별 포지션으로 펼쳐 수집한다. */
@Component
class TossRecruitmentSite : RecruitmentSite {
    override val definition = SourceDefinition("toss", "토스", "https://toss.im/career/jobs")

    override fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) {
        browser.navigate(definition.listingUrl)
        val response = browser.evaluate("""async () => {
            const r = await fetch('https://api-public.toss.im/api/v3/ipd-eggnog/career/job-groups');
            if (!r.ok) throw new Error('토스 목록 HTTP ' + r.status);
            return await r.json();
        }""") as? Map<*, *> ?: error("토스 목록 응답이 올바르지 않습니다.")
        check(response["resultType"] == "SUCCESS") { "토스 목록 조회 실패: ${response["error"]}" }
        val groups = response["success"] as? List<*> ?: error("토스 공고 묶음이 없습니다.")
        val seen = mutableSetOf<String>()
        groups.forEach { group ->
            val jobs = (group as? Map<*, *>)?.get("jobs") as? List<*> ?: error("토스 개별 포지션 목록이 없습니다.")
            jobs.forEach jobLoop@ { raw ->
                val job = raw as? Map<*, *> ?: error("토스 포지션 응답 형식 변경")
                val id = (job["id"] as? Number)?.toLong()?.toString() ?: error("토스 포지션 ID 누락")
                if (!seen.add(id)) return@jobLoop
                val metadata = (job["metadata"] as? List<*>)?.mapNotNull { value ->
                    (value as? Map<*, *>)?.let { (it["id"] as? Number)?.toLong()?.toString().orEmpty() to it["value"] }
                }?.toMap() ?: error("토스 직무 메타데이터 누락")
                if (job["internal_job_id"] == null || metadata["5038345003"] == true || metadata["5038345003"] == "t") return@jobLoop
                val title = job["title"]?.toString().orEmpty()
                val company = metadata["4169410003"]?.toString().orEmpty()
                val category = metadata["24623243003"]?.toString().orEmpty()
                if (!TossJobRule.accepts(category, title) || company.isBlank() || company == "기타") return@jobLoop
                val encodedCompany = URLEncoder.encode(company, StandardCharsets.UTF_8)
                emit(DiscoveredPosting("https://toss.im/career/job-detail?job_id=$id&company=$encodedCompany", company, title,
                    (job["location"] as? Map<*, *>)?.get("name")?.toString().orEmpty()))
            }
        }
    }

    override fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        browser.waitFor("#__NEXT_DATA__")
        val result = browser.evaluate("""() => {
            const state = JSON.parse(document.querySelector('#__NEXT_DATA__').textContent);
            const queries = state.props?.pageProps?.prefetchResult?.dehydratedState?.queries;
            if (!Array.isArray(queries)) throw new Error('토스 상세 데이터 구조 변경');
            const query = queries.find(q => q.queryKey?.[1] === 'job-detail');
            if (!query) throw new Error('토스 상세 데이터 누락');
            return JSON.parse(query.state.data);
        }""") as? Map<*, *> ?: error("토스 상세 응답 형식 변경")
        val job = result["job"] as? Map<*, *> ?: error("토스 공고 상세를 가져오지 못했습니다: ${result["errorStatus"]}")
        val expectedId = Regex("(?:^|&)(?:job_id|gh_jid)=(\\d+)(?:&|$)").find(URI(posting.url).rawQuery.orEmpty())?.groupValues?.get(1)
        check((job["id"] as? Number)?.toLong()?.toString() == expectedId) { "토스 상세 공고 ID가 일치하지 않습니다." }
        val title = job["title"]?.toString().orEmpty()
        if (!TossJobRule.accepts(job["subCategory"]?.toString().orEmpty(), title) || job["isProspect"] == true) {
            throw PostingExcluded("토스 직무 규칙에서 제외: ${job["subCategory"]} / $title")
        }
        browser.waitFor("h1")
        val body = browser.evaluate("""() => {
            const heading = [...document.querySelectorAll('h1')].find(e => e.getClientRects().length);
            const container = heading?.closest('.p-container__inner');
            if (!container || container.innerText.length < 120) throw new Error('토스 상세 본문 렌더링 실패');
            return container.innerText.trim();
        }""")?.toString().orEmpty()
        val expiryDate = job["expiryDate"]?.toString()?.takeIf { it.isNotBlank() }
        val closed = expiryDate?.let { date ->
            runCatching {
                val localDate = LocalDate.parse(date.take(10))
                val time = job["expiryTime"]?.toString()?.let { Regex("\\d{1,2}:\\d{2}").find(it)?.value }?.let(LocalTime::parse) ?: LocalTime.MAX
                ZonedDateTime.of(localDate, time, ZoneId.of("Asia/Seoul")).toInstant().isBefore(Instant.now())
            }.getOrDefault(false)
        } ?: false
        return ExtractedPosting(title, job["company"]?.toString().orEmpty().ifBlank { posting.company },
            job["location"]?.toString().orEmpty().ifBlank { posting.location }, body, closed)
    }

    override fun acceptsUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "toss.im" && uri.path == "/career/job-detail" &&
            Regex("(?:^|&)(?:job_id|gh_jid)=\\d+(?:&|$)").containsMatchIn(uri.rawQuery.orEmpty())
    }.getOrDefault(false)
}

/** 토스가 제공하는 Job Category를 우선하고 모호한 직무는 제외한다. */
private object TossJobRule {
    private val developmentCategories = setOf("Backend", "Frontend", "App", "Data Engineering", "ML", "Infra", "Device", "병역특례")
    private val excludedTitle = Regex("(?<![A-Za-z])QA(?![A-Za-z])|quality|test engineer|scientist|analyst|분석가|인재\\s*(?:풀|pool)|talent\\s*pool|게임|game (?:server|client)|기획|디자인|디자이너|어시스턴트|assistant|IT 운영|IT 담당|IDC|network|security|보안|Technical Writer|제품 관리자", RegexOption.IGNORE_CASE)
    private val softwareTitle = Regex("개발|엔지니어|engineer|developer|DevOps|SRE", RegexOption.IGNORE_CASE)
    private val businessExcluded = Regex("인재\\s*(?:풀|pool)|talent\\s*pool|researcher|리서처|analyst|분석가|sales\\s*(?:operations|support|enablement)|세일즈\\s*(?:운영|지원|교육)|영업\\s*(?:운영|지원)", RegexOption.IGNORE_CASE)
    private val businessTitles = mapOf(
        "Product Ownership" to Regex("product\\s*(?:owner|manager)|프로덕트\\s*(?:오너|매니저)|\\b(?:PM|PO)\\b", RegexOption.IGNORE_CASE),
        "Product Operations" to Regex("product\\s*manager|프로덕트\\s*매니저", RegexOption.IGNORE_CASE),
        "Product Design" to Regex("designer|디자이너|디자인", RegexOption.IGNORE_CASE),
        "Platform Design" to Regex("designer|디자이너|디자인", RegexOption.IGNORE_CASE),
        "Brand Design" to Regex("designer|디자이너|디자인", RegexOption.IGNORE_CASE),
        "Marketing" to Regex("marketing|마케팅|마케터|brand\\s*manager|브랜드\\s*매니저", RegexOption.IGNORE_CASE),
        "Sales" to Regex("sales|세일즈|영업|business\\s*development|account\\s*manager", RegexOption.IGNORE_CASE),
        "Strategy" to Regex("기획|전략\\s*매니저|planning|planner|strategy\\s*manager|marketing\\s*manager|마케팅\\s*매니저", RegexOption.IGNORE_CASE),
        "Bank" to Regex("(?:수신상품|여신상품|여신제도|기업여신)\\s*(?:심사)?기획", RegexOption.IGNORE_CASE),
        "IT Planning" to Regex("IT\\s*(?:기획|전략)|IT\\s*(?:planning|strategy)", RegexOption.IGNORE_CASE),
    )
    fun accepts(category: String, title: String): Boolean =
        (category in developmentCategories && !excludedTitle.containsMatchIn(title) && softwareTitle.containsMatchIn(title)) ||
            (!businessExcluded.containsMatchIn(title) && businessTitles[category]?.containsMatchIn(title) == true)
}
