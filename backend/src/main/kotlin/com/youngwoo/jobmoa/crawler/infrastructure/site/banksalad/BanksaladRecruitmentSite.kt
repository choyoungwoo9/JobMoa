package com.youngwoo.jobmoa.crawler.infrastructure.site.banksalad

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import com.youngwoo.jobmoa.crawler.infrastructure.site.GreetingClosure
import org.springframework.stereotype.Component
import java.net.URI

/** 뱅크샐러드 공식 채용 목록을 기준으로 테크 공고 링크만 Greeting 상세에 연결한다. */
@Component
class BanksaladRecruitmentSite:RecruitmentSite {
    override val definition=SourceDefinition("banksalad","뱅크샐러드","https://corp.banksalad.com/jobs/")
    private val role=Regex("""engineer|developer|개발|엔지니어|devops|\bSRE\b|데이터 엔지니어|machine learning""",RegexOption.IGNORE_CASE)
    private val excluded=Regex("""product manager|project manager|analyst|scientist|보안|security|마케팅|영업|인재풀|기획|데이터 분석""",RegexOption.IGNORE_CASE)
    override fun discover(browser:BrowserPage,emit:(DiscoveredPosting)->Unit) {
        browser.navigate(definition.listingUrl);browser.waitFor("#__NEXT_DATA__")
        val rows=browser.evaluate("""() => [...document.querySelectorAll('a')].filter(a=>a.href.includes('banksalad.career.greetinghr.com/')&&/\/o\/\d+/.test(new URL(a.href).pathname)).map(a=>({url:a.href,title:a.innerText.trim(),context:a.parentElement?.innerText||''}));""") as? List<*> ?: error("뱅크샐러드 공고 링크 구조 변경")
        rows.forEach { raw -> val row=raw as? Map<*,*> ?: error("뱅크샐러드 공고 형식 변경")
            val title=row["title"]?.toString().orEmpty().lineSequence().firstOrNull().orEmpty().trim();val url=row["url"]?.toString().orEmpty()
            if(!role.containsMatchIn(title)||excluded.containsMatchIn(title))return@forEach
            if(!acceptsUrl(url))error("뱅크샐러드 공식 상세 URL 형식 변경: $url")
            emit(DiscoveredPosting(url,"뱅크샐러드",title,"서울"))
        }
    }
    override fun closureReason(browser:BrowserPage,posting:DiscoveredPosting):String?=if(acceptsUrl(posting.url))GreetingClosure.reason(browser,posting.url) else null
    override fun extract(browser:BrowserPage,posting:DiscoveredPosting):ExtractedPosting {
        require(acceptsUrl(posting.url));browser.waitFor("#__NEXT_DATA__");browser.waitFor(".ql-editor")
        val d=browser.evaluate("""() => {const p=JSON.parse(document.querySelector('#__NEXT_DATA__').textContent).props.pageProps;const q=p.dehydratedState?.queries?.find(x=>x.queryKey?.includes('getOpeningById'));const i=q?.state?.data?.data?.openingsInfo;const b=[...document.querySelectorAll('.ql-editor')].find(e=>e.getBoundingClientRect().height>0)?.innerText||'';return {title:i?.title,company:i?.groupInfo?.name,body:b,status:i?.status,due:i?.dueDate};}""") as? Map<*,*> ?: error("뱅크샐러드 상세 구조 변경")
        val title=d["title"]?.toString().orEmpty().trim();if(!role.containsMatchIn(title)||excluded.containsMatchIn(title))throw PostingExcluded("뱅크샐러드 테크 직무 규칙에서 제외: $title")
        val body=d["body"]?.toString().orEmpty().trim();check(title==posting.title.trim()&&body.length>=100) { "뱅크샐러드 상세 제목 또는 본문 누락" }
        return ExtractedPosting(title,d["company"]?.toString().orEmpty().ifBlank { "뱅크샐러드" },"서울",body,d["status"] in setOf("CLOSED","CLOSE","FINISHED")||d["due"]?.toString()?.take(10)?.let { it<java.time.LocalDate.now().toString() }==true)
    }
    override fun acceptsUrl(url:String):Boolean=runCatching {val u=URI(url);u.scheme=="https"&&u.host=="banksalad.career.greetinghr.com"&&u.path.matches(Regex("/(?:ko/)?o/[1-9]\\d*/?"))&&u.rawQuery.isNullOrBlank()}.getOrDefault(false)
}
