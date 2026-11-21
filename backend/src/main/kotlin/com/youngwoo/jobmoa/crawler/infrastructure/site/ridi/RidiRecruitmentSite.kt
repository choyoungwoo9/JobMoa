package com.youngwoo.jobmoa.crawler.infrastructure.site.ridi

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import org.springframework.stereotype.Component
import java.net.URI

/** RoundHR의 RIDI 공개 공고 API와 공식 상세 페이지를 사용한다. */
@Component
class RidiRecruitmentSite : RecruitmentSite {
    override val definition=SourceDefinition("ridi","리디","https://ridi.recruit.roundhr.com/")
    private val api="https://api-prod.roundhr.com/api/site/jobs?code=ridi&per=50&page=1"
    private val technical=Regex("""개발|엔지니어|developer|engineer|\bSRE\b|devops|개발자|백엔드|프론트엔드|풀스택|데이터|android|ios|machine learning""",RegexOption.IGNORE_CASE)
    private val excluded=Regex("""PD|MD|디자인|디자이너|운영|기획|영업|마케팅|분석|scientist|analyst|security|보안|QA|채용|인재풀""",RegexOption.IGNORE_CASE)
    override fun discover(browser: BrowserPage,emit:(DiscoveredPosting)->Unit) {
        val response=browser.readJson(api) as? Map<*,*> ?: error("리디 목록 API 응답 형식 변경")
        val rows=response["results"] as? List<*> ?: error("리디 목록 결과 누락")
        rows.forEach { raw ->
            val row=raw as? Map<*,*> ?: error("리디 공고 형식 변경")
            val title=row["title"]?.toString().orEmpty().trim()
            val form=row["application_form"] as? Map<*,*> ?: return@forEach
            val code=form["code"]?.toString().orEmpty()
            val group=(row["position_group"] as? Map<*,*>)?.get("title")?.toString().orEmpty()
            val status=form["status"]?.toString().orEmpty()
            if(status!="in_progress" || group!="제품/개발" || !technical.containsMatchIn(title) || excluded.containsMatchIn(title) || code.isBlank()) return@forEach
            emit(DiscoveredPosting("https://ridi.recruit.roundhr.com/c/$code?back=true","리디",title))
        }
    }
    override fun extract(browser:BrowserPage,posting:DiscoveredPosting):ExtractedPosting {
        require(acceptsUrl(posting.url)) { "미지원 리디 상세 주소" }
        val data=browser.evaluate("""() => {const main=document.querySelector('main')||document.body;const h=main.querySelector('h1');return {title:h?.innerText?.trim()||'',body:main.innerText.trim(),closed:/지원 마감|마감된 공고|채용 마감/.test(main.innerText)};}""") as? Map<*,*> ?: error("리디 상세 화면을 읽지 못했습니다.")
        val title=data["title"]?.toString().orEmpty().trim(); val body=data["body"]?.toString().orEmpty().trim()
        if(!technical.containsMatchIn(title)||excluded.containsMatchIn(title)) throw PostingExcluded("리디 제품/개발 분야 외 공고: $title")
        check(title==posting.title.trim() && body.length>=100) { "리디 상세 제목 또는 본문 누락" }
        return ExtractedPosting(title,"리디","서울",body,data["closed"]==true)
    }
    override fun acceptsUrl(url:String):Boolean=runCatching { val u=URI(url);u.scheme=="https"&&u.host=="ridi.recruit.roundhr.com"&&u.path.matches(Regex("/c/[A-Za-z0-9]+")) }.getOrDefault(false)
}
