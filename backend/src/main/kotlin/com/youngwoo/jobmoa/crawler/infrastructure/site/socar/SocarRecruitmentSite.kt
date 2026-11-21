package com.youngwoo.jobmoa.crawler.infrastructure.site.socar

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import com.youngwoo.jobmoa.crawler.infrastructure.site.GreetingClosure
import org.springframework.stereotype.Component
import java.net.URI

/** 쏘카 공식 Next.js 목록의 jobGroup 코드로 개발·데이터 포지션만 선택하고 Greeting 상세를 저장한다. */
@Component
class SocarRecruitmentSite:RecruitmentSite {
    override val definition=SourceDefinition("socar","쏘카","https://www.socarcorp.kr/careers/jobs")
    override val discoveryTargets=listOf(definition,SourceDefinition("socar-nineone","나인투원","https://www.socarcorp.kr/careers/jobs?company=nineone"))
    override fun discover(browser:BrowserPage,emit:(DiscoveredPosting)->Unit)=discoverAt(browser,definition.listingUrl,emit)
    override fun discoverAt(browser:BrowserPage,listingUrl:String,emit:(DiscoveredPosting)->Unit) {
        require(URI(listingUrl).host=="www.socarcorp.kr")
        browser.navigate("https://www.socarcorp.kr/careers/jobs");browser.waitFor("#__NEXT_DATA__")
        val data=browser.evaluate("""() => {const p=JSON.parse(document.querySelector('#__NEXT_DATA__').textContent).props.pageProps;const r=p.jobList?.jsonResult;const groups=p.codeList?.jobGroup?.jsonResult?.data;return {rows:r?.data,total:Number(r?.metaData?.totalCount),groups};}""") as? Map<*,*> ?: error("쏘카 공식 공고 목록 구조 변경")
        val rows=data["rows"] as? List<*> ?: error("쏘카 공식 공고 목록 누락")
        val total=(data["total"] as? Number)?.toInt() ?: error("쏘카 공고 전체 건수 누락")
        check(rows.size==total) { "쏘카 목록 일부만 표시됨: ${rows.size}/$total" }
        val groups=(data["groups"] as? List<*>)?.mapNotNull { it as? Map<*,*> }?.associate { it["code"]?.toString().orEmpty() to it["code_name"]?.toString().orEmpty() }.orEmpty()
        rows.forEach { raw -> val row=raw as? Map<*,*> ?: error("쏘카 공고 항목 형식 변경")
            val title=row["title"]?.toString().orEmpty().trim(); if(groups[row["job_group_code"]?.toString()]!="개발/데이터" || !ROLE.containsMatchIn(title) || EXCLUDED.containsMatchIn(title)) return@forEach
            val company=row["company_name"]?.toString().orEmpty(); val portal=if(company=="나인투원") "socar-nineone" else "socar"
            if(portal=="socar-nineone" && !listingUrl.contains("company=nineone")) return@forEach
            if(portal=="socar" && listingUrl.contains("company=nineone")) return@forEach
            val url=row["notice_url"]?.toString().orEmpty();check(acceptsUrl(url)) { "쏘카 공식 목록 상세 URL 미지원: $url" }
            emit(DiscoveredPosting(url,company,title,"서울"))
        }
    }
    override fun closureReason(browser:BrowserPage,posting:DiscoveredPosting):String?=if(acceptsUrl(posting.url)) GreetingClosure.reason(browser,posting.url) else null
    override fun extract(browser:BrowserPage,posting:DiscoveredPosting):ExtractedPosting {
        require(acceptsUrl(posting.url));browser.waitFor("#__NEXT_DATA__");browser.waitFor(".ql-editor")
        val d=browser.evaluate("""() => {const p=JSON.parse(document.querySelector('#__NEXT_DATA__').textContent).props.pageProps;const qs=p.dehydratedState?.queries||[];const q=qs.find(x=>x.queryKey?.includes('getOpeningById'));const i=q?.state?.data?.data?.openingsInfo;const body=[...document.querySelectorAll('.ql-editor')].find(e=>e.getBoundingClientRect().height>0)?.innerText||'';return {title:i?.title,company:i?.groupInfo?.name,body,status:i?.status,due:i?.dueDate};}""") as? Map<*,*> ?: error("쏘카 상세 구조 변경")
        val title=d["title"]?.toString().orEmpty().trim();if(!ROLE.containsMatchIn(title)||EXCLUDED.containsMatchIn(title))throw PostingExcluded("쏘카 개발/데이터 후보에서 제외: $title")
        val body=d["body"]?.toString().orEmpty().trim();check(title==posting.title.trim()&&body.length>=100) { "쏘카 상세 제목 또는 본문 누락" }
        return ExtractedPosting(title,d["company"]?.toString().orEmpty().ifBlank { posting.company },posting.location,body,d["status"] in setOf("CLOSED","CLOSE","FINISHED")||d["due"]?.toString()?.let { it.take(10)<java.time.LocalDate.now().toString() }==true)
    }
    override fun acceptsUrl(url:String):Boolean=runCatching {val u=URI(url);u.scheme=="https"&&u.host=="socar.career.greetinghr.com"&&u.path.matches(Regex("/(?:ko/)?o/[1-9]\\d*/?"))&&u.rawQuery.isNullOrBlank()}.getOrDefault(false)
    companion object {private val ROLE=Regex("""data engineer|software|back.?end|front.?end|android|\bios\b|developer|engineer|개발자|엔지니어|개발|데이터""",RegexOption.IGNORE_CASE);private val EXCLUDED=Regex("""QA|quality|security|보안|기획|PM|analyst|분석|scientist|사이언티스트|hardware|품질""",RegexOption.IGNORE_CASE)}
}
