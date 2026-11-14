package com.youngwoo.jobmoa.crawler.infrastructure.site.kakao

import com.youngwoo.jobmoa.crawler.domain.model.*
import com.youngwoo.jobmoa.crawler.domain.port.*
import java.net.URI
import java.time.*
import java.time.format.DateTimeFormatter

/** 각 포털의 구조 해석만 묶는다. 판별 조건은 계열사마다 별도로 둔다. */
internal object KakaoAffiliatePortals {
    val targets = listOf(
        SourceDefinition("kakaopay", "카카오페이", "https://careers.kakaopay.com/ko/main"),
        SourceDefinition("kakaobank", "카카오뱅크", "https://recruit.kakaobank.com/jobs"),
        SourceDefinition("kakaopaysec", "카카오페이증권", "https://career.kakaopaysec.com/job_posting"),
        SourceDefinition("kakaomobility", "카카오모빌리티", "https://kakaomobility.career.greetinghr.com/ko/guide"),
        SourceDefinition("kakaoenterprise", "카카오엔터프라이즈", "https://careers.kakaoenterprise.com/ko/job"),
        SourceDefinition("kakaoent", "카카오엔터테인먼트", "https://careers.kakaoent.com/ko/job"),
        SourceDefinition("kakaostyle", "카카오스타일", "https://career.kakaostyle.com/jobs"),
        SourceDefinition("kakaovx", "카카오VX", "https://kakaovxrecruiter.ninehire.site/jobs"),
        SourceDefinition("kakaopayins", "카카오페이손해보험", "https://career.kakaopayinscorp.co.kr/ko/apply"),
        SourceDefinition("dktechin", "디케이테크인", "https://dktechin.career.greetinghr.com/ko/guide"),
        SourceDefinition("kidsnote", "키즈노트", "https://kidsnote.career.greetinghr.com/ko/guide")
    )
    private val ninehireIds = setOf("kakaopaysec", "kakaostyle", "kakaovx")
    fun target(url: String): SourceDefinition? = targets.find { URI(it.listingUrl).host == URI(url).host }
    fun acceptsUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        val target = target(url) ?: return false
        uri.scheme == "https" && when (target.id) {
            "kakaobank" -> Regex("/jobs/[0-9]+").matches(uri.path)
            in ninehireIds -> Regex("/job_posting/[A-Za-z0-9_-]+").matches(uri.path)
            else -> Regex("/ko/o/[0-9]+").matches(uri.path)
        }
    }.getOrDefault(false)

    fun discover(browser: BrowserPage, target: SourceDefinition, emit: (DiscoveredPosting) -> Unit) {
        when (target.id) {
            "kakaobank" -> discoverBank(browser, emit)
            in ninehireIds -> discoverNinehire(browser, target, emit)
            else -> discoverGreeting(browser, target, emit)
        }
    }

    fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting = when (target(posting.url)?.id) {
        "kakaobank" -> extractBank(browser, posting)
        in ninehireIds -> extractNinehire(browser, posting)
        else -> extractGreeting(browser, posting)
    }

    private fun discoverGreeting(browser: BrowserPage, target: SourceDefinition, emit: (DiscoveredPosting) -> Unit) {
        browser.navigate(target.listingUrl)
        browser.waitFor("#__NEXT_DATA__")
        val rows = browser.evaluate("""() => {
            const props = JSON.parse(document.querySelector('#__NEXT_DATA__').textContent).props.pageProps;
            const query = props.dehydratedState?.queries.find(q => q.queryKey?.[0] === 'openings');
            if (!query || !Array.isArray(query.state.data)) throw new Error('Greeting 전체 공고 데이터 누락');
            return query.state.data;
        }""") as? List<*> ?: error("${target.name} 목록 구조 변경")
        val seen = mutableSetOf<String>()
        rows.forEach { raw ->
            val row = raw as? Map<*, *> ?: error("${target.name} 공고 형식 변경")
            val id = row["openingId"]?.toString() ?: error("${target.name} 공고 ID 누락")
            val title = row["title"]?.toString().orEmpty()
            val positions = ((row["openingJobPosition"] as? Map<*, *>)?.get("openingJobPositions") as? List<*>)
                ?.mapNotNull { it as? Map<*, *> }.orEmpty()
            if (!seen.add(id) || row["deploy"] != true || deadlinePassed(row["dueDate"]?.toString()) || !greetingDeveloper(target.id, title, positions)) return@forEach
            val company = (row["group"] as? Map<*, *>)?.get("name")?.toString().orEmpty().ifBlank { target.name }
            emit(DiscoveredPosting("https://${URI(target.listingUrl).host}/ko/o/$id", company, title, locations(positions)))
        }
    }

    private fun extractGreeting(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        val target = target(posting.url) ?: error("미지원 카카오 계열사 포털")
        browser.waitFor("#__NEXT_DATA__")
        val data = browser.evaluate("""() => {
            const props = JSON.parse(document.querySelector('#__NEXT_DATA__').textContent).props.pageProps;
            const result = props.dehydratedState?.queries.find(q => q.queryKey?.[1] === 'getOpeningById')?.state.data;
            if (result?.success !== true || !result.data?.openingsInfo) throw new Error('Greeting 상세 데이터 누락');
            const data = result.data;
            return {info:data.openingsInfo, company:data.groupInfo?.name, positions:data.jobPositionSetting?.jobPositions || []};
        }""") as? Map<*, *> ?: error("${target.name} 상세 구조 변경")
        val info = data["info"] as? Map<*, *> ?: error("${target.name} 상세 누락")
        check(info["openingId"].toString() == URI(posting.url).path.substringAfterLast('/')) { "카카오 계열사 공고 ID 불일치" }
        val title = info["title"]?.toString().orEmpty()
        val positions = (data["positions"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
        if (!greetingDeveloper(target.id, title, positions)) throw PostingExcluded("${target.name} 직무 규칙에서 제외: $title")
        val body = htmlText(browser, info["detail"]?.toString().orEmpty())
        check(body.length >= 120) { "${target.name} 상세 본문 누락" }
        val closed = info["status"] in setOf("CLOSED", "CLOSE", "FINISHED") || deadlinePassed(info["dueDate"]?.toString())
        return ExtractedPosting(title, data["company"]?.toString().orEmpty().ifBlank { target.name }, locations(positions), body, closed)
    }

    private fun greetingDeveloper(portal: String, title: String, positions: List<Map<*, *>>): Boolean {
        if (Regex("인재.?풀|인재.?pool|talent.?pool|scientist|research|(?<![A-Za-z])QA(?![A-Za-z])|품질|보안|security|(?<![A-Za-z])HW(?![A-Za-z])|하드웨어|mechanical|게임|game (?:server|client)|테크니션|기획|디자이너|디자인|(?<![A-Za-z])PM(?![A-Za-z])|analyst|분석가|컨설턴트|assistant|어시스턴트", RegexOption.IGNORE_CASE).containsMatchIn(title)) return false
        val occupations = positions.mapNotNull { (it["workspaceOccupation"] as? Map<*, *>)?.get("occupation")?.toString() }
        val jobs = positions.mapNotNull { (it["workspaceJob"] as? Map<*, *>)?.get("job")?.toString() }
        return when (portal) {
            "kakaomobility" -> "개발" in jobs && Regex("개발자|개발.?엔지니어|백엔드|프론트엔드|풀스택|서버 개발|소프트웨어|SLAM|(?:AI|ML|데이터).*엔지니어|(?:data|machine learning|android|ios|full.?stack).*engineer|시스템 엔지니어|DevOps|SRE", RegexOption.IGNORE_CASE).containsMatchIn(title)
            "kakaopay" -> occupations.any { it in setOf("개발", "기술", "테크", "Tech") } && Regex("개발|engineer|엔지니어|DevOps|SRE", RegexOption.IGNORE_CASE).containsMatchIn(title)
            "kakaoenterprise" -> "Tech" in occupations && Regex("개발자|개발.?엔지니어|백엔드|프론트엔드|풀스택|software|(?:AI|ML|데이터).*엔지니어|(?:data|machine learning|android|ios|full.?stack).*engineer|DevOps|SRE", RegexOption.IGNORE_CASE).containsMatchIn(title)
            "kakaogames" -> "개발" in occupations && jobs.any { it in setOf("데이터개발", "어플리케이션개발", "웹서비스개발", "플랫폼개발") }
            "kakaopayins" -> occupations.any { it in setOf("Tech", "테크", "기술", "개발") } && jobs.any {
                it in setOf("Backend", "Frontend", "Server", "서버개발", "클라이언트개발", "시스템개발", "인프라개발", "Android", "iOS", "Fullstack", "Full-stack", "Data Engineering", "ML", "AI", "DevOps", "SRE")
            } && Regex("개발|engineer|엔지니어|DevOps|SRE", RegexOption.IGNORE_CASE).containsMatchIn(title)
            "dktechin" -> occupations.any { it in setOf("Backend", "Frontend", "Android", "iOS", "Fullstack", "Full-stack", "Data Engineering", "ML", "AI", "DevOps", "SRE") } && Regex("개발|engineer|엔지니어|DevOps|SRE", RegexOption.IGNORE_CASE).containsMatchIn(title)
            "kidsnote" -> "개발" in occupations && "개발" in jobs && Regex("개발자|engineer|엔지니어", RegexOption.IGNORE_CASE).containsMatchIn(title)
            "kakaoent" -> occupations.any { it in setOf("개발", "기술", "Tech", "테크", "개발/기술") } && Regex("개발|engineer|엔지니어|DevOps|SRE", RegexOption.IGNORE_CASE).containsMatchIn(title)
            else -> false
        }
    }

    private fun discoverBank(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) {
        browser.navigate("https://recruit.kakaobank.com/jobs")
        var page = 1
        val seen = mutableSetOf<String>()
        while (true) {
            val response = browser.evaluate("""async page => {
                const r = await fetch('/api/recruits', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({pageNumber:page,pageSize:100})});
                if (!r.ok) throw new Error('카카오뱅크 목록 HTTP ' + r.status);
                return await r.json();
            }""", page) as? Map<*, *> ?: error("카카오뱅크 목록 구조 변경")
            val rows = response["list"] as? List<*> ?: error("카카오뱅크 공고 목록 누락")
            val paging = response["paging"] as? Map<*, *> ?: error("카카오뱅크 페이지 정보 누락")
            val pages = (paging["totalPages"] as? Number)?.toInt() ?: error("카카오뱅크 페이지 수 누락")
            var fresh = 0
            rows.forEach { raw ->
                val row = raw as? Map<*, *> ?: error("카카오뱅크 공고 형식 변경")
                val id = row["recruitNoticeSn"].toString()
                if (!seen.add(id)) return@forEach
                fresh++
                if (!bankDeveloper(row) || bankDeadlinePassed(row)) return@forEach
                emit(DiscoveredPosting("https://recruit.kakaobank.com/jobs/$id", "카카오뱅크", row["recruitNoticeName"].toString()))
            }
            if (page >= pages) return
            check(fresh > 0 && page < 10_000) { "카카오뱅크 목록 페이지 반복" }
            page++
        }
    }

    private fun extractBank(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        val id = URI(posting.url).path.substringAfterLast('/')
        val job = browser.evaluate("""async id => {
            const r = await fetch('/api/recruits/' + id);
            if (!r.ok) throw new Error('카카오뱅크 상세 HTTP ' + r.status);
            return await r.json();
        }""", id) as? Map<*, *> ?: error("카카오뱅크 상세 구조 변경")
        check(job["recruitNoticeSn"].toString() == id) { "카카오뱅크 공고 ID 불일치" }
        if (!bankDeveloper(job)) throw PostingExcluded("카카오뱅크 직무 규칙에서 제외: ${job["recruitNoticeName"]}")
        val body = htmlText(browser, job["contents"]?.toString().orEmpty())
        check(body.length >= 120) { "카카오뱅크 본문 누락" }
        val location = Regex("근무(?:지|장소)\\s*[:：]?\\s*([^\\n]+)").find(body)?.groupValues?.get(1)?.trim().orEmpty()
        browser.waitFor("h1, h2, h3")
        return ExtractedPosting(job["recruitNoticeName"].toString(), "카카오뱅크", location, body, bankDeadlinePassed(job))
    }

    private fun bankDeveloper(job: Map<*, *>): Boolean {
        val title = job["recruitNoticeName"]?.toString().orEmpty()
        // 카카오뱅크는 서버·계정계·데이터·AI 개발을 Engineering과 별도 직군으로 공개한다.
        return job["recruitClassName"] in setOf("Engineering", "Server", "Core Banking", "Data", "AI") &&
            !Regex("(?<![A-Za-z])QA(?![A-Za-z])|보안|security|품질|기획|analyst|분석가|scientist|research|게임|game (?:server|client)|네트워크|DBA|IT 운영|인재\\s*(?:풀|pool)|어시스턴트|assistant", RegexOption.IGNORE_CASE).containsMatchIn(title) &&
            Regex("개발|engineer|엔지니어|DevOps|SRE", RegexOption.IGNORE_CASE).containsMatchIn(title)
    }

    private fun bankDeadlinePassed(job: Map<*, *>): Boolean = job["receiveEndDatetime"]?.toString()?.let {
        LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.of("Asia/Seoul")).toInstant().isBefore(Instant.now())
    } ?: false

    private fun discoverNinehire(browser: BrowserPage, target: SourceDefinition, emit: (DiscoveredPosting) -> Unit) {
        browser.navigate(target.listingUrl)
        browser.waitFor("#__NEXT_DATA__")
        val companyId = browser.evaluate("""() => JSON.parse(document.querySelector('#__NEXT_DATA__').textContent).props.pageProps.homepageProps.homepage.companyId""")?.toString()
            ?: error("Ninehire 회사 ID 누락")
        var page = 1
        val seen = mutableSetOf<String>()
        while (true) {
            val result = browser.evaluate("""async args => {
                const r = await fetch('/_backend/identity-access/homepage/recruitments?companyId=' + encodeURIComponent(args.companyId) + '&page=' + args.page + '&countPerPage=100&order=created_at_desc');
                if (!r.ok) throw new Error('Ninehire 목록 HTTP ' + r.status);
                return await r.json();
            }""", mapOf("companyId" to companyId, "page" to page)) as? Map<*, *> ?: error("Ninehire 목록 구조 변경")
            val rows = result["results"] as? List<*> ?: error("Ninehire 목록 누락")
            val total = (result["count"] as? Number)?.toInt() ?: error("Ninehire 전체 건수 누락")
            var fresh = 0
            rows.forEach { raw ->
                val job = raw as? Map<*, *> ?: error("Ninehire 공고 형식 변경")
                val id = job["recruitmentId"].toString()
                if (!seen.add(id)) return@forEach
                fresh++
                if (job["status"] != "in_progress" || job["jobPostingIsActive"] != true || job["isListExposed"] == false || !ninehireDeveloper(target.id, job)) return@forEach
                val key = job["addressKey"]?.toString() ?: error("Ninehire 상세 주소 누락")
                emit(DiscoveredPosting("https://${URI(target.listingUrl).host}/job_posting/$key", target.name, job["externalTitle"].toString(), securitiesLocations(job)))
            }
            if (seen.size >= total) return
            check(fresh > 0 && page < 10_000) { "Ninehire 목록 페이지 반복" }
            page++
        }
    }

    private fun extractNinehire(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        val target = target(posting.url) ?: error("미지원 Ninehire 포털")
        browser.waitFor("#__NEXT_DATA__")
        val result = browser.evaluate("""() => {
            const p = JSON.parse(document.querySelector('#__NEXT_DATA__').textContent).props.pageProps;
            if (!p.recruitment || !p.jobPosting) throw new Error('Ninehire 상세 데이터 누락');
            return {job:p.recruitment,html:p.jobPosting.content};
        }""") as? Map<*, *> ?: error("Ninehire 상세 구조 변경")
        val job = result["job"] as? Map<*, *> ?: error("Ninehire 상세 누락")
        check(job["addressKey"].toString() == URI(posting.url).path.substringAfterLast('/')) { "Ninehire 상세 ID 불일치" }
        if (!ninehireDeveloper(target.id, job)) throw PostingExcluded("${target.name} 직무 규칙에서 제외: ${job["externalTitle"]}")
        val body = htmlText(browser, result["html"]?.toString().orEmpty())
        check(body.length >= 120) { "Ninehire 본문 누락" }
        val closed = job["status"] in setOf("closed", "finished", "ended") || job["closedAt"] != null || deadlinePassed(job["endAt"]?.toString())
        return ExtractedPosting(job["externalTitle"].toString(), target.name, securitiesLocations(job), body, closed)
    }

    /** 새 수집과 CAPTURE 단계 재시도 모두 같은 원본 화면 변형을 적용한다. */
    fun prepareCapture(browser: BrowserPage, posting: DiscoveredPosting) {
        if (target(posting.url)?.id !in ninehireIds) return
        browser.waitFor("#__NEXT_DATA__")
        browser.evaluate("""() => {
            // Ninehire 본문은 absolute 내부 스크롤 + overflow:hidden 조상 안에 있다.
            // 본문과 조상을 함께 펼쳐야 전체 페이지 PNG가 마지막 문단까지 포함된다.
            const panes = [...document.querySelectorAll('*')].filter(element => {
                const style = getComputedStyle(element);
                return element.clientHeight > 100 && element.scrollHeight > element.clientHeight + 50 && /auto|scroll/.test(style.overflowY);
            });
            for (const pane of panes) {
                pane.style.setProperty('position', 'relative', 'important');
                for (let element = pane; element; element = element.parentElement) {
                    element.style.setProperty('height', 'auto', 'important');
                    element.style.setProperty('min-height', '0', 'important');
                    element.style.setProperty('max-height', 'none', 'important');
                    element.style.setProperty('overflow', 'visible', 'important');
                    element.style.setProperty('flex', '0 0 auto', 'important');
                }
            }
        }""")
    }

    private fun ninehireDeveloper(portal: String, job: Map<*, *>): Boolean {
        val group = (job["jobGroup"] as? Map<*, *>)?.get("title")?.toString().orEmpty()
        val title = job["externalTitle"]?.toString().orEmpty()
        val task = (job["jobTask"] as? Map<*, *>)?.get("title")?.toString().orEmpty()
        if (Regex("(?<![A-Za-z])QA(?![A-Za-z])|보안|security|기획|analyst|분석가|scientist|research|인재풀|인재.?pool|게임|game (?:server|client)|디자이너|품질|운영 매니저", RegexOption.IGNORE_CASE).containsMatchIn(title)) return false
        return when (portal) {
            "kakaopaysec" -> group in setOf("백엔드 개발", "프론트엔드 개발", "서버 개발", "Android 개발", "iOS 개발", "풀스택 개발", "데이터 엔지니어링", "플랫폼 개발", "개발", "AI 엔지니어링", "DevOps", "SRE")
            "kakaostyle" -> group == "Tech" && (task in setOf("Backend", "Frontend", "Android", "iOS", "Fullstack", "Full-stack") ||
                task in setOf("Data", "Infra") && Regex("MLOps|DevOps|SRE|데이터\\s*엔지니어|Data Engineer|(?:AI|ML|머신러닝)\\s*엔지니어|Machine Learning Engineer", RegexOption.IGNORE_CASE).containsMatchIn(title))
            "kakaovx" -> group == "개발" && Regex("개발|프로그래머|engineer", RegexOption.IGNORE_CASE).containsMatchIn(title) ||
                group == "기술" && task in setOf("DevOps", "SRE", "Data Engineering", "ML", "AI") && Regex("개발|엔지니어|engineer|DevOps|SRE", RegexOption.IGNORE_CASE).containsMatchIn(title)
            else -> false
        }
    }

    private fun securitiesLocations(job: Map<*, *>): String = (job["jobLocations"] as? List<*>)
        ?.mapNotNull { (it as? Map<*, *>)?.get("addressName")?.toString() }?.distinct()?.joinToString(" / ").orEmpty()
    private fun locations(positions: List<Map<*, *>>): String = positions.mapNotNull {
        (it["workspacePlace"] as? Map<*, *>)?.get("place")?.toString()
    }.distinct().joinToString(" / ")
    private fun deadlinePassed(value: String?): Boolean = value?.takeIf { it.isNotBlank() }?.let { Instant.parse(it).isBefore(Instant.now()) } ?: false
    private fun htmlText(browser: BrowserPage, html: String): String = browser.evaluate("""html => {
        const doc = new DOMParser().parseFromString(html, 'text/html');
        doc.querySelectorAll('script,style').forEach(e => e.remove());
        doc.querySelectorAll('br').forEach(e => e.replaceWith(doc.createTextNode('\n')));
        doc.querySelectorAll('p,li,div,h1,h2,h3,h4,blockquote,tr').forEach(e => e.append(doc.createTextNode('\n')));
        return doc.body.textContent.replace(/[\u200B-\u200D\uFEFF]/g, '').replace(/\n[ \t]*\n[ \t]*\n/g, '\n\n').trim();
    }""", html)?.toString().orEmpty()
}
