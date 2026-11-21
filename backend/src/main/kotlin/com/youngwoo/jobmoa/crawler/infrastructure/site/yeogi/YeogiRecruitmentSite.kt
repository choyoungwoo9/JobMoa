package com.youngwoo.jobmoa.crawler.infrastructure.site.yeogi

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import org.springframework.stereotype.Component
import java.net.URI

/** 여기어때 공식 채용연계 인턴 공고. Cloudflare 차단을 우회하지 않고 일반 페이지 접근만 시도한다. */
@Component
class YeogiRecruitmentSite:RecruitmentSite {
    override val definition=SourceDefinition("yeogi","여기어때","https://internship.yeogi.com/")
    private val role=Regex("""backend|back-end|frontend|front-end|infra|data engineer|security engineer|개발|엔지니어|데이터 엔지니어|보안 엔지니어""",RegexOption.IGNORE_CASE)
    private val excluded=Regex("""analyst|scientist|기획|마케팅|디자인|인재풀|영업|운영""",RegexOption.IGNORE_CASE)
    override fun discover(browser:BrowserPage,emit:(DiscoveredPosting)->Unit) {
        browser.navigate(definition.listingUrl)
        val rows=browser.evaluate("""() => [...document.querySelectorAll('a[href]')].map(a=>({url:a.href,title:a.innerText.trim()})).filter(x=>x.title&&/developer|engineer|개발|엔지니어|backend|frontend/i.test(x.title));""") as? List<*> ?: error("여기어때 공식 인턴 목록 구조 변경")
        rows.forEach { raw -> val row=raw as? Map<*,*> ?: return@forEach;val title=row["title"]?.toString().orEmpty();val url=row["url"]?.toString().orEmpty()
            if(!role.containsMatchIn(title)||excluded.containsMatchIn(title))return@forEach
            if(!acceptsUrl(url))return@forEach
            emit(DiscoveredPosting(url,"여기어때컴퍼니",title,"서울"))
        }
    }
    override fun extract(browser:BrowserPage,posting:DiscoveredPosting):ExtractedPosting {
        require(acceptsUrl(posting.url));val data=browser.evaluate("""() => {const main=document.querySelector('main')||document.body;return {title:main.querySelector('h1')?.innerText||document.title,body:main.innerText,closed:/모집 마감|지원 마감|채용 마감/.test(main.innerText)};}""") as? Map<*,*> ?: error("여기어때 상세 구조 변경")
        val title=data["title"]?.toString().orEmpty().trim();val body=data["body"]?.toString().orEmpty().trim()
        if(!role.containsMatchIn(title)||excluded.containsMatchIn(title))throw PostingExcluded("여기어때 기술 공고 외 직무: $title")
        check(title==posting.title.trim()&&body.length>=100) { "여기어때 상세 제목 또는 본문 누락" }
        return ExtractedPosting(title,"여기어때컴퍼니","서울",body,data["closed"]==true)
    }
    override fun acceptsUrl(url:String):Boolean=runCatching {val u=URI(url);u.scheme=="https"&&u.host=="internship.yeogi.com"&&u.path!="/"&&u.path.length<300&&u.rawQuery.isNullOrBlank()}.getOrDefault(false)
}
