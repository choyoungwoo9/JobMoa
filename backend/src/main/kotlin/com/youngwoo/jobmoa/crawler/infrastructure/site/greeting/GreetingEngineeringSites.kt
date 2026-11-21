package com.youngwoo.jobmoa.crawler.infrastructure.site.greeting

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import com.youngwoo.jobmoa.crawler.infrastructure.site.GreetingClosure
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Instant

/** Greeting 공개 채용 포털의 공통 API 형태를 사용하되, 개발 후보 판별은 포털별 필드와 제목으로 제한한다. */
abstract class GreetingEngineeringSite(
    override val definition: SourceDefinition,
    private val acceptedHosts: Set<String>,
    private val roleGroups: Set<String>,
    private val roleNames: Set<String>,
) : RecruitmentSite {
    override val discoveryTargets: List<SourceDefinition> = if (definition.id == "musinsa") listOf(
        "무신사" to "musinsa",
        "29CM" to "musinsa-29cm",
        "무신사페이먼츠" to "musinsa-payments",
        "무신사 스탠다드" to "musinsa-standard",
        "무신사로지스틱스" to "musinsa-logistics",
        "무신사 글로벌" to "musinsa-global",
    ).map { (company, id) -> SourceDefinition(id, company, "https://www.musinsacareers.com/ko/home?company=" + java.net.URLEncoder.encode(company, "UTF-8")) }
    else listOf(definition)

    override fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) = discoverAt(browser, definition.listingUrl, emit)

    override fun discoverAt(browser: BrowserPage, listingUrl: String, emit: (DiscoveredPosting) -> Unit) {
        require(URI(listingUrl).host in acceptedHosts) { "미지원 Greeting 목록 주소: $listingUrl" }
        browser.navigate(listingUrl)
        browser.waitFor("#__NEXT_DATA__")
        val rows = browser.evaluate("""() => {
            const p=JSON.parse(document.querySelector('#__NEXT_DATA__').textContent).props.pageProps;
            const q=p.dehydratedState?.queries?.find(x=>Array.isArray(x.queryKey)&&x.queryKey[0]==='openings');
            if(!q||!Array.isArray(q.state.data)) throw new Error('Greeting 공고 목록 데이터 누락');
            return q.state.data;
        }""") as? List<*> ?: error("${definition.name} 목록 구조 변경")
        rows.forEach { item ->
            val row = item as? Map<*, *> ?: error("${definition.name} 공고 항목 형식 변경")
            val title = row["title"]?.toString().orEmpty().trim()
            val id = row["openingId"]?.toString()?.takeIf { it.matches(Regex("[1-9]\\d*")) }
                ?: error("${definition.name} 공고 ID 누락")
            val positions = ((row["openingJobPosition"] as? Map<*, *>)?.get("openingJobPositions") as? List<*>)
                ?.mapNotNull { it as? Map<*, *> }.orEmpty()
            if (row["deploy"] != true || pastDue(row["dueDate"]?.toString()) || !developerCandidate(title, positions)) return@forEach
            val company = (row["group"] as? Map<*, *>)?.get("name")?.toString().orEmpty().ifBlank { definition.name }
            val selectedCompany = runCatching { URI(listingUrl).rawQuery?.substringAfter("company=")?.let { java.net.URLDecoder.decode(it, "UTF-8") } }.getOrNull()
            if (!selectedCompany.isNullOrBlank() && company != selectedCompany) return@forEach
            emit(DiscoveredPosting("https://${URI(listingUrl).host}/ko/o/$id", company, title, locations(positions)))
        }
    }

    override fun closureReason(browser: BrowserPage, posting: DiscoveredPosting): String? =
        if (acceptsUrl(posting.url)) GreetingClosure.reason(browser, posting.url) else null

    override fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        require(acceptsUrl(posting.url)) { "미지원 ${definition.name} 상세 주소" }
        browser.waitFor("#__NEXT_DATA__")
        browser.waitFor(".ql-editor")
        val id = URI(posting.url).path.substringAfterLast('/')
        val data = browser.evaluate("""expected => {
            const p=JSON.parse(document.querySelector('#__NEXT_DATA__').textContent).props.pageProps;
            const r=p.dehydratedState?.queries?.find(x=>x.queryKey?.includes('getOpeningById'))?.state?.data;
            if(r?.success!==true||!r.data?.openingsInfo) throw new Error('Greeting 상세 공고 데이터 누락');
            const d=r.data, i=d.openingsInfo;
            if(String(i.openingId)!==expected) throw new Error('상세 URL과 공고 ID가 다릅니다.');
            const clean=s=>String(s||'').replace(/<script[\s\S]*?<\/script>/gi,' ').replace(/<style[\s\S]*?<\/style>/gi,' ').replace(/<[^>]+>/g,' ').replace(/&nbsp;/g,' ').replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/\s+/g,' ').trim();
            const el=[...document.querySelectorAll('.ql-editor')].find(e=>e.getBoundingClientRect().height>0);
            return {id:String(i.openingId),title:i.title,company:d.groupInfo?.name,location:(d.jobPositionSetting?.jobPositions||[]).map(x=>x.workspacePlace).filter(Boolean).map(x=>[x.place,x.detailPlace].filter(Boolean).join(' ')).join(' / '),body:el?.innerText?.trim()||clean(i.detail),status:i.status,due:i.dueDate};
        }""", id) as? Map<*, *> ?: error("${definition.name} 상세 구조 변경")
        val title = data["title"]?.toString().orEmpty().trim()
        check(title.isNotBlank() && title == posting.title.trim()) { "${definition.name} 공고 제목이 목록과 일치하지 않습니다." }
        val body = data["body"]?.toString().orEmpty().trim()
        check(body.length >= 100) { "${definition.name} 상세 본문 누락" }
        return ExtractedPosting(title, data["company"]?.toString().orEmpty().ifBlank { posting.company },
            data["location"]?.toString().orEmpty().ifBlank { posting.location }, body,
            data["status"] in setOf("CLOSED", "CLOSE", "FINISHED", "EXPIRED") || pastDue(data["due"]?.toString()))
    }

    override fun acceptsUrl(url: String): Boolean = runCatching {
        val u=URI(url); u.scheme=="https" && u.host in acceptedHosts && u.userInfo==null &&
            (u.port==-1||u.port==443) && u.path.matches(Regex("/(?:ko/)?o/[1-9]\\d*/?")) && u.rawQuery.isNullOrBlank() && u.rawFragment.isNullOrBlank()
    }.getOrDefault(false)

    private fun developerCandidate(title: String, positions: List<Map<*, *>>): Boolean {
        if (title.isBlank() || NON_DEV.containsMatchIn(title)) return false
        val jobs = positions.mapNotNull { (it["workspaceJob"] as? Map<*, *>)?.get("job")?.toString() }
        val divisions = positions.mapNotNull { (it["workspaceDivision"] as? Map<*, *>)?.get("division")?.toString() }
        val occupations = positions.mapNotNull { (it["workspaceOccupation"] as? Map<*, *>)?.get("occupation")?.toString() }
        val official = jobs.any { it in roleNames } || divisions.any { it in roleGroups } || occupations.any { it in roleGroups }
        return official && DEV_TITLE.containsMatchIn(title)
    }

    private fun locations(positions: List<Map<*, *>>): String = positions.mapNotNull { it["workspacePlace"] as? Map<*, *> }
        .mapNotNull { listOfNotNull(it["place"]?.toString(), it["detailPlace"]?.toString()).filter(String::isNotBlank).joinToString(" ").ifBlank { null } }
        .distinct().joinToString(" / ")

    private fun pastDue(raw: String?): Boolean = raw?.takeIf(String::isNotBlank)?.let { runCatching { Instant.parse(it).isBefore(Instant.now()) }.getOrDefault(false) } ?: false

    companion object {
        private val DEV_TITLE = Regex("""software|engineer|developer|개발|엔지니어|데이터|머신러닝|machine learning|devops|\bSRE\b|\bandroid\b|\bios\b|풀스택|백엔드|프론트엔드""", RegexOption.IGNORE_CASE)
        private val NON_DEV = Regex("""인재.?풀|talent.?pool|product manager|project manager|\bPM\b|기획|디자이너|디자인|마케팅|영업|sales|analyst|분석가|scientist|사이언티스트|QA|SDET|security|보안|recruiter""", RegexOption.IGNORE_CASE)
    }
}

@Component class KurlyRecruitmentSite : GreetingEngineeringSite(SourceDefinition("kurly", "컬리", "https://kurly.career.greetinghr.com/ko/recruiting"), setOf("kurly.career.greetinghr.com"), setOf("테크"), setOf("개발", "인프라"))
@Component class MusinsaRecruitmentSite : GreetingEngineeringSite(SourceDefinition("musinsa", "무신사", "https://www.musinsacareers.com/ko/home"), setOf("www.musinsacareers.com", "musinsacareers.com"), setOf("무신사", "29CM", "무신사페이먼츠", "무신사 스탠다드", "무신사로지스틱스", "무신사 글로벌"), setOf("AI Native Engineer", "Backend Engineering", "DevOps", "ML Engineer", "Frontend Engineering", "Data Engineering", "DBA", "Security Engineering"))
@Component class ZigbangRecruitmentSite : GreetingEngineeringSite(SourceDefinition("zigbang", "직방", "https://zigbang.career.greetinghr.com/ko/home"), setOf("zigbang.career.greetinghr.com"), setOf("Engineering", "개발", "Tech"), setOf("개발", "개발자", "Backend", "Frontend", "Android", "iOS", "Data Engineering", "ML Engineer", "DevOps", "SRE"))
