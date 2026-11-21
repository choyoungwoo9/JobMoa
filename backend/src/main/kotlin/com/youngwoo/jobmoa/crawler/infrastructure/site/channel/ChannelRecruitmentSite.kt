package com.youngwoo.jobmoa.crawler.infrastructure.site.channel

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import org.springframework.stereotype.Component
import java.net.URI

/** 채널톡 공개 채용 목록에 포함된 Engineering 역할만 후보로 허용한다. */
@Component
class ChannelRecruitmentSite : RecruitmentSite {
    override val definition = SourceDefinition("channel", "채널코퍼레이션", "https://channel.io/kr/careers")
    private val titleRule = Regex("""software engineer|back.?end|front.?end|full.?stack|data engineer|ml engineer|applied ai engineer|devops engineer|android engineer|ios engineer|developer|개발자|엔지니어|\bSRE\b|devops""", RegexOption.IGNORE_CASE)
    private val reject = Regex("""recruit|sales|support|security|analyst|scientist|product manager|project manager|consultant|account|solutions engineer|deployment|마케팅|기획|영업|보안|분석|채용""", RegexOption.IGNORE_CASE)

    override fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) {
        browser.navigate(definition.listingUrl)
        browser.waitFor("#__NEXT_DATA__")
        val rows = browser.evaluate("""() => {
            const p=JSON.parse(document.querySelector('#__NEXT_DATA__').textContent).props.pageProps;
            if(!Array.isArray(p.jobs)) throw new Error('채널톡 공개 채용 목록이 없습니다.');
            return p.jobs.map(j=>({id:j.id,title:j.text,team:j.categories?.team||j.categories?.department||'',country:j.country||'',body:j.descriptionPlain||j.descriptionBodyPlain||''}));
        }""") as? List<*> ?: error("채널톡 목록 구조 변경")
        rows.forEach { raw ->
            val row=raw as? Map<*, *> ?: error("채널톡 공고 항목 형식 변경")
            val title=row["title"]?.toString().orEmpty().trim()
            if (!titleRule.containsMatchIn(title) || reject.containsMatchIn(title)) return@forEach
            val id=row["id"]?.toString()?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) } ?: error("채널톡 공고 ID 누락")
            val location=if(row["country"]?.toString()=="KR") "서울" else row["country"]?.toString().orEmpty()
            emit(DiscoveredPosting("https://channel.io/kr/careers/$id", "채널코퍼레이션", title, location))
        }
    }

    override fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        require(acceptsUrl(posting.url)) { "미지원 채널톡 상세 주소" }
        val result=browser.evaluate("""() => {
            const p=JSON.parse(document.querySelector('#__NEXT_DATA__').textContent).props.pageProps;
            const jobs=Array.isArray(p.jobs)?p.jobs:[]; const id=location.pathname.split('/').pop();
            const j=jobs.find(x=>x.id===id);
            const main=document.querySelector('main');
            return {title:j?.text||main?.querySelector('h1')?.innerText||'',body:j?.descriptionBodyPlain||j?.descriptionPlain||main?.innerText||'',closed:/expired|closed|마감/i.test(main?.innerText||'')};
        }""") as? Map<*, *> ?: error("채널톡 상세 구조 변경")
        val title=result["title"]?.toString().orEmpty().trim()
        if (!titleRule.containsMatchIn(title) || reject.containsMatchIn(title)) throw PostingExcluded("채널톡 Engineering 직무 규칙에서 제외: $title")
        val body=result["body"]?.toString().orEmpty().trim()
        check(title==posting.title.trim() && body.length>=100) { "채널톡 상세 제목 또는 본문 누락" }
        return ExtractedPosting(title,"채널코퍼레이션", "서울",body,result["closed"]==true)
    }

    override fun acceptsUrl(url: String): Boolean = runCatching {
        val u=URI(url); u.scheme=="https" && u.host=="channel.io" && u.path.matches(Regex("/kr/careers/[0-9a-fA-F-]{36}")) && u.rawQuery.isNullOrBlank()
    }.getOrDefault(false)
}
