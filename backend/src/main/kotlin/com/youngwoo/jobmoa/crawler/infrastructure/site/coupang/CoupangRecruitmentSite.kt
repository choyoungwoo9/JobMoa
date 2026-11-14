package com.youngwoo.jobmoa.crawler.infrastructure.site.coupang

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime

/** 쿠팡 공식 채용 포털이 사용하는 Greenhouse 공개 보드 전체를 탐색한다. */
@Component
class CoupangRecruitmentSite : RecruitmentSite {
    override val definition = SourceDefinition("coupang", "쿠팡", "https://www.coupang.jobs/en/jobs/")
    private val boardUrl = "https://boards-api.greenhouse.io/v1/boards/coupang"

    override fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) {
        browser.navigate(boardUrl)
        // content=true 목록은 12MB 이상 본문을 JSON DOM으로 렌더링해 브라우저 메모리를 급증시킨다.
        // 31바이트 보드 정보만 탐색하고, 본문 없는 목록과 부서별 ID 매핑을 메모리에서 합친다.
        val response = browser.evaluate("""async () => {
            const get = async path => {
                const response = await fetch('/v1/boards/coupang/' + path);
                if (!response.ok) throw new Error('쿠팡 공개 API 응답 오류: ' + response.status + ' ' + path);
                return response.json();
            };
            const listing = await get('jobs');
            const organization = await get('departments');
            if (!Array.isArray(listing.jobs) || !Array.isArray(organization.departments)) throw new Error('쿠팡 공개 API 목록 구조 변경');
            const byId = new Map();
            for (const department of organization.departments) {
                if (!Array.isArray(department.jobs) || typeof department.name !== 'string') throw new Error('쿠팡 부서별 목록 구조 변경');
                for (const job of department.jobs) {
                    const key = String(job.id);
                    if (!byId.has(key)) byId.set(key, []);
                    byId.get(key).push({name: department.name});
                }
            }
            return {meta: listing.meta, jobs: listing.jobs.map(job => {
                const departments = byId.get(String(job.id));
                if (!departments) throw new Error('쿠팡 공고 부서 정보 누락: ' + job.id);
                return {id: job.id, title: job.title, absolute_url: job.absolute_url, location: job.location, departments};
            })};
        }""") as? Map<*, *> ?: error("쿠팡 공개 채용 보드 응답 형식 변경")
        val rows = response["jobs"] as? List<*> ?: error("쿠팡 채용 목록이 없습니다.")
        val total = ((response["meta"] as? Map<*, *>)?.get("total") as? Number)?.toInt()
            ?: error("쿠팡 전체 공고 수를 확인하지 못했습니다.")
        val seen = mutableSetOf<String>()
        check(rows.size == total) { "쿠팡 전체 $total 건 중 ${rows.size} 건만 조회됐습니다. 공개 보드 페이지 형식을 확인하세요." }
        rows.forEach { value ->
            val row = value as? Map<*, *> ?: error("쿠팡 공고 항목 형식 변경")
            val id = row["id"]?.toString() ?: error("쿠팡 공고 ID 누락")
            check(seen.add(id)) { "쿠팡 목록에 중복 공고 ID가 있습니다: $id" }
            val title = row["title"]?.toString().orEmpty()
            val departments = departmentNames(row)
            if (!CoupangDeveloperRule.accepts(title, departments)) return@forEach
            val url = row["absolute_url"]?.toString().orEmpty()
            check(acceptsUrl(url)) { "미지원 쿠팡 공고 주소: $url" }
            val location = (row["location"] as? Map<*, *>)?.get("name")?.toString().orEmpty()
            emit(DiscoveredPosting(url, companyName(title, departments), title, location))
        }
    }

    override fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        // 보안 확인 화면을 공고로 저장하지 않는다. 접근 제한은 일반 실패로 기록하고 재시도한다.
        val pageText = browser.text("body").orEmpty()
        check(!Regex("checking your browser|verify you are human|보안 확인 수행 중|사람인지 확인|Just a moment", RegexOption.IGNORE_CASE).containsMatchIn(pageText)) {
            "쿠팡 상세 페이지 접근이 보안 확인으로 제한되었습니다. 확인 화면은 공고 본문·캡처로 저장하지 않습니다."
        }
        val id = Regex("(?:^|&)gh_jid=(\\d+)(?:&|$)").find(URI(posting.url).rawQuery.orEmpty())?.groupValues?.get(1)
            ?: error("쿠팡 상세 주소에 gh_jid가 없습니다.")
        // 원문에 렌더링된 내용과 공식 보드 본문을 대조한다. API만 성공해도 차단 화면을 캡처하지 않는다.
        val response = browser.evaluate("""async id => {
            const response = await fetch('https://boards-api.greenhouse.io/v1/boards/coupang/jobs/' + encodeURIComponent(id));
            if (!response.ok) throw new Error('쿠팡 상세 API 응답 오류: ' + response.status);
            const job = await response.json();
            const decoded = new DOMParser().parseFromString(job.content || '', 'text/html').body.textContent || '';
            const documentBody = new DOMParser().parseFromString(decoded, 'text/html').body;
            documentBody.querySelectorAll('script,style').forEach(e => e.remove());
            documentBody.querySelectorAll('p,div,li,h1,h2,h3,h4,br').forEach(e => e.append(document.createTextNode('\n')));
            const body = documentBody.textContent.trim();
            const normalize = text => text.replace(/\s+/g, ' ').trim();
            const visible = normalize(document.body.innerText);
            const bodyBeginning = normalize(body).slice(0, 120);
            return {job, body, rendered: visible.includes(normalize(job.title)) && bodyBeginning.length > 40 && visible.includes(bodyBeginning)};
        }""", id) as? Map<*, *> ?: error("쿠팡 상세 응답 형식 변경")
        val row = response["job"] as? Map<*, *> ?: error("쿠팡 상세 공고 정보 누락")
        val title = row["title"]?.toString().orEmpty()
        val departments = departmentNames(row)
        if (!CoupangDeveloperRule.accepts(title, departments)) throw PostingExcluded("쿠팡 개발 직무·조직 규칙에서 제외: $title / $departments")
        check(response["rendered"] == true) { "쿠팡 원문 페이지에 공고 제목·본문이 정상 렌더링되지 않았습니다. 차단/오류 화면을 저장하지 않습니다." }
        val body = response["body"]?.toString().orEmpty()
        check(body.length >= 100) { "쿠팡 공고 본문이 비어 있거나 너무 짧습니다: $title" }
        val deadline = row["application_deadline"]?.toString()?.let {
            runCatching { Instant.parse(it) }.getOrElse { _ -> runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        }
        val closed = browser.evaluate("""() => Array.from(document.querySelectorAll('main h1, main h2, main [role="alert"], .job-closed, .job-expired')).some(e => /^(This job is no longer available|This job has expired|채용이 마감되었습니다|마감된 공고)[.!\s]*$/i.test(e.innerText.trim()))""") == true
        return ExtractedPosting(title, companyName(title, departments), (row["location"] as? Map<*, *>)?.get("name")?.toString().orEmpty().ifBlank { posting.location }, body,
            closed || deadline?.isBefore(Instant.now()) == true)
    }

    override fun acceptsUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host in setOf("www.coupang.jobs", "coupang.jobs") &&
            Regex("/(?:en|kr|ko)/jobs/?").matches(uri.path) && Regex("(?:^|&)gh_jid=\\d+(?:&|$)").containsMatchIn(uri.rawQuery.orEmpty())
    }.getOrDefault(false)

    private fun departmentNames(row: Map<*, *>): List<String> = (row["departments"] as? List<*>)
        ?.mapNotNull { (it as? Map<*, *>)?.get("name")?.toString() }.orEmpty()

    private fun companyName(title: String, departments: List<String>): String {
        val prefix = Regex("^\\[([^]]+)]").find(title)?.groupValues?.get(1).orEmpty()
        if (prefix in setOf("쿠팡페이", "쿠팡플레이", "쿠팡이츠", "쿠팡이츠서비스", "쿠팡풀필먼트서비스", "쿠팡로지스틱스서비스", "Coupang Pay", "Coupang Play")) return when (prefix) {
            "Coupang Pay" -> "쿠팡페이"
            "Coupang Play" -> "쿠팡플레이"
            else -> prefix
        }
        when (prefix.uppercase()) {
            "CLS" -> return "쿠팡로지스틱스서비스"
            "CFS" -> return "쿠팡풀필먼트서비스"
            "CPLB" -> return "CPLB"
            "ROCKET NOW" -> return "CP One Japan (Rocket Now)"
        }
        return when {
            departments.any { it.startsWith("Coupang Pay") } -> "쿠팡페이"
            departments.any { it.startsWith("Coupang Play") } -> "쿠팡플레이"
            departments.any { it.startsWith("Coupang Financial") } -> "쿠팡파이낸셜"
            departments.any { it == "Coupang Fulfillment Services (CFS)" || it.startsWith("(CFS)") } -> "쿠팡풀필먼트서비스"
            departments.any { it == "Coupang Logistics Services (CLS)" || it == "Logistics (CLS)" } -> "쿠팡로지스틱스서비스"
            departments.any { it == "Coupang Eats Services" } -> "쿠팡이츠서비스"
            departments.any { it == "CPLB" } -> "CPLB"
            departments.any { it == "CP One Japan (Rocket Now)" } -> "CP One Japan (Rocket Now)"
            else -> "쿠팡"
        }
    }
}

/** 쿠팡은 기술 조직 소속과 구체적인 소프트웨어 직무명을 함께 확인한다. */
private object CoupangDeveloperRule {
    private val excluded = Regex("\\b(?:QA|SDET|test engineer|security|hardware|mechanical|scientist|analyst|manager|director|recruiter|sales)\\b|품질|보안|기계|설비|채용|영업|물류.*운영", RegexOption.IGNORE_CASE)
    private val software = Regex("software engineer|software developer|back[ -]?end|front[ -]?end|full[ -]?stack|(?:android|ios|machine learning|data|ai|platform|site reliability) engineer|\\bSRE\\b|소프트웨어.*(?:개발|엔지니어)|백엔드|프론트엔드|서버.*개발|데이터.*엔지니어", RegexOption.IGNORE_CASE)
    private val technology = setOf("Technology", "eCommerce Engineering", "Tech Infrastructure", "Search and Discovery", "Catalog Tech", "Coupang Intelligent Cloud (CIC)", "Global Operations Technology (Global Ops Tech)", "Corporate IT", "Marketing System", "Supernova")
    fun accepts(title: String, departments: List<String>): Boolean = !excluded.containsMatchIn(title) && software.containsMatchIn(title) &&
        departments.any { it in technology || it.endsWith(" Tech") }
}
