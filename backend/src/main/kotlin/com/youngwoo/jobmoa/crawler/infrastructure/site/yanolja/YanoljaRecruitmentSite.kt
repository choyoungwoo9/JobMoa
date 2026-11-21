package com.youngwoo.jobmoa.crawler.infrastructure.site.yanolja

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import org.springframework.stereotype.Component
import java.net.URI

/** Workday 공개 CXS API 사용. 실제 공고명으로 개발 직무를 제한하며 R&D 분류만으로 후보를 만들지 않는다. */
@Component
class YanoljaRecruitmentSite:RecruitmentSite {
    override val definition=SourceDefinition("yanolja","야놀자","https://yanolja.wd102.myworkdayjobs.com/External_Yanolja")
    private val api="https://yanolja.wd102.myworkdayjobs.com/wday/cxs/yanolja/External_Yanolja"
    private val role=Regex("""software|backend|back-end|frontend|front-end|full.?stack|android|\bios\b|data engineer|machine learning|\bML engineer|devops|\bSRE\b|developer|engineer|개발|엔지니어|백엔드|프론트엔드""",RegexOption.IGNORE_CASE)
    private val excluded=Regex("""product owner|product manager|project manager|QA|analyst|scientist|기획|분석|보안|영업|마케팅|인재풀""",RegexOption.IGNORE_CASE)
    override fun discover(browser:BrowserPage,emit:(DiscoveredPosting)->Unit) {
        browser.navigate(definition.listingUrl)
        val data=browser.evaluate("""async base=>{const r=await fetch(base+'/jobs',{method:'POST',headers:{'Content-Type':'application/json','Accept':'application/json'},body:JSON.stringify({appliedFacets:{},limit:100,offset:0,searchText:''})});if(!r.ok)throw new Error('Workday 목록 HTTP '+r.status);return r.json();}""",api) as? Map<*,*> ?: error("야놀자 Workday 목록 구조 변경")
        val rows=data["jobPostings"] as? List<*> ?: error("야놀자 Workday 공고 목록 누락")
        val total=(data["total"] as? Number)?.toInt() ?: error("야놀자 Workday 전체 건수 누락")
        check(rows.size>=total) { "야놀자 Workday 목록 일부만 조회됐습니다: ${rows.size}/$total" }
        rows.forEach { raw ->
            val row=raw as? Map<*,*> ?: error("Workday 공고 형식 변경")
            val title=row["title"]?.toString().orEmpty().trim(); val path=row["externalPath"]?.toString().orEmpty()
            if(title.isBlank()||!role.containsMatchIn(title)||excluded.containsMatchIn(title)) return@forEach
            check(path.startsWith("/job/") && !path.contains("..")) { "Workday 공고 경로가 유효하지 않습니다." }
            val loc=row["locationsText"]?.toString().orEmpty()
            emit(DiscoveredPosting("${definition.listingUrl}$path","YANOLJA",title,loc))
        }
    }
    override fun extract(browser:BrowserPage,posting:DiscoveredPosting):ExtractedPosting {
        require(acceptsUrl(posting.url)) { "미지원 야놀자 상세 주소" }
        val r=browser.evaluate("""() => {const main=document.querySelector('[data-automation-id="jobPostingDescription"]')||document.querySelector('main')||document.body;return {title:document.querySelector('[data-automation-id="jobPostingHeader"]')?.innerText||document.querySelector('h1')?.innerText||'',body:main.innerText.trim(),closed:/no longer available|job has expired|지원이 마감|채용 마감/i.test(document.body.innerText)};}""") as? Map<*,*> ?: error("야놀자 상세 화면 구조 변경")
        val title=r["title"]?.toString().orEmpty().trim();val body=r["body"]?.toString().orEmpty().trim()
        if(!role.containsMatchIn(title)||excluded.containsMatchIn(title))throw PostingExcluded("야놀자 개발 직무 규칙에서 제외: $title")
        check(title==posting.title.trim()&&body.length>=100) { "야놀자 Workday 상세 제목 또는 본문 누락" }
        return ExtractedPosting(title,posting.company,posting.location,body,r["closed"]==true)
    }
    override fun acceptsUrl(url:String):Boolean=runCatching {val u=URI(url);u.scheme=="https"&&u.host=="yanolja.wd102.myworkdayjobs.com"&&u.path.startsWith("/External_Yanolja/job/")&&u.path.length<500&&u.rawQuery.isNullOrBlank()}.getOrDefault(false)
}
