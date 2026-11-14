package com.youngwoo.jobmoa.crawler.infrastructure.site.bucketplace

import com.youngwoo.jobmoa.crawler.domain.model.DiscoveredPosting
import com.youngwoo.jobmoa.crawler.domain.model.ExtractedPosting
import com.youngwoo.jobmoa.crawler.domain.model.PostingExcluded
import com.youngwoo.jobmoa.crawler.domain.model.SourceDefinition
import com.youngwoo.jobmoa.crawler.domain.port.BrowserPage
import com.youngwoo.jobmoa.crawler.domain.port.RecruitmentSite
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Instant

/** 공식 회사 채용 목록의 Gatsby 원본 데이터와, 그 목록에서 연결한 Greeting 상세를 사용한다. */
@Component
class BucketplaceRecruitmentSite : RecruitmentSite {
    override val definition = SourceDefinition("bucketplace", "오늘의집", "https://www.bucketplace.com/careers/")
    private val listingDataUrl = "https://www.bucketplace.com/page-data/careers/page-data.json"

    override fun discover(browser: BrowserPage, emit: (DiscoveredPosting) -> Unit) {
        // 전체 한국어 목록과 일본어 목록이 하나의 정적 GraphQL 결과에 담겨 있다. 제목 검색/페이지 UI에 의존하지 않는다.
        val page = browser.readJson(listingDataUrl).asObject("오늘의집 공식 목록")
        check(page["path"] == "/careers/") { "오늘의집 목록 데이터 경로가 변경되었습니다." }
        val data = page["result"].asObject("오늘의집 목록 result")["data"].asObject("오늘의집 목록 data")
        val nodes = listOf("position", "positionJp").flatMap { key ->
            data[key].asObject("오늘의집 $key")["nodes"] as? List<*>
                ?: error("오늘의집 $key 전체 공고 목록이 없습니다.")
        }
        val seen = mutableSetOf<String>()
        nodes.forEach { value ->
            val row = value.asObject("오늘의집 목록 항목")["frontmatter"].asObject("오늘의집 공고 메타데이터")
            if (row["isPool"] == true) return@forEach
            val title = row.string("name").trim()
            check(title.isNotBlank()) { "오늘의집 목록 공고명이 없습니다." }
            if (!BucketplaceCandidateRule.accepts(title, row.string("teamName"))) return@forEach
            val original = row.string("recruitUrl")
            check(acceptsUrl(original)) { "오늘의집 공식 목록에서 미지원 공고 주소가 발견되었습니다: $original" }
            val url = canonicalizeUrl(original)
            if (!seen.add(url)) return@forEach
            emit(DiscoveredPosting(url, "오늘의집", title, row.string("region").trim()))
        }
    }

    override fun extract(browser: BrowserPage, posting: DiscoveredPosting): ExtractedPosting {
        val result = inspectDetail(browser, posting)
        val title = result.string("title").trim()
        if (!BucketplaceCandidateRule.accepts(title)) throw PostingExcluded("오늘의집 지원 직무 범위에서 제외: $title")
        val body = result.string("body").trim()
        val due = result.string("dueDate").takeIf(String::isNotBlank)?.let { value ->
            runCatching { Instant.parse(value) }.getOrElse { error("오늘의집 마감일 형식 변경: $value") }
        }
        val company = result.string("company").trim().let { name ->
            when (name) {
                "오늘의집", "Bucketplace", "버킷플레이스" -> "오늘의집"
                else -> name.ifBlank { error("오늘의집 공식 상세의 회사명이 없습니다.") }
            }
        }
        return ExtractedPosting(title, company, result.string("location").ifBlank { posting.location }, body,
            result.string("status") in setOf("CLOSED", "CLOSE", "EXPIRED") || due?.isBefore(Instant.now()) == true)
    }

    override fun prepareCapture(browser: BrowserPage, posting: DiscoveredPosting) {
        // 본문을 이미 저장한 CAPTURE 단계 재시도에서도 오류/보안 화면을 캡처하지 않는다.
        val result = inspectDetail(browser, posting)
        check(result.string("title").trim() == posting.title.trim()) { "오늘의집 저장 본문과 캡처 대상의 공고명이 달라졌습니다." }
    }

    override fun canonicalizeUrl(url: String): String {
        require(acceptsUrl(url)) { "지원하지 않는 오늘의집 상세 주소입니다: $url" }
        val id = URI(url).path.trimEnd('/').substringAfterLast('/')
        return "https://bucketplace.career.greetinghr.com/ko/o/$id"
    }

    override fun acceptsUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "bucketplace.career.greetinghr.com" && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) && uri.path.matches(Regex("/(?:ko/)?o/[1-9]\\d*/?")) &&
            uri.rawQuery.isNullOrBlank() && uri.rawFragment.isNullOrBlank()
    }.getOrDefault(false)

    private fun inspectDetail(browser: BrowserPage, posting: DiscoveredPosting): Map<*, *> {
        require(acceptsUrl(posting.url)) { "지원하지 않는 오늘의집 상세 주소입니다." }
        browser.waitFor("#__NEXT_DATA__")
        browser.waitFor(".ql-editor")
        val id = URI(posting.url).path.trimEnd('/').substringAfterLast('/')
        val result = browser.evaluate("""expectedId => {
            const clean = text => String(text || '').replace(/[\u200B-\u200D\uFEFF]/g, '').replace(/\r\n?/g, '\n').trim();
            const normalize = text => clean(text).replace(/\s+/g, ' ');
            const page = JSON.parse(document.querySelector('#__NEXT_DATA__').textContent).props?.pageProps;
            const query = page?.dehydratedState?.queries?.find(q => Array.isArray(q.queryKey) && q.queryKey.includes('getOpeningById'));
            const response = query?.state?.data;
            if (response?.success !== true) throw new Error('오늘의집 Greeting 상세 응답 실패: ' + (response?.message || response?.errorCode || '본문 메타데이터 없음'));
            const data = response.data;
            const info = data?.openingsInfo;
            if (!info || String(info.openingId) !== expectedId) throw new Error('오늘의집 상세 URL과 공고 ID가 다릅니다.');
            const title = clean(info.title);
            const editor = Array.from(document.querySelectorAll('.ql-editor')).find(element => {
                const rect = element.getBoundingClientRect();
                return rect.width > 0 && rect.height > 0 && getComputedStyle(element).visibility !== 'hidden';
            });
            const body = clean(editor?.innerText).replace(/[ \t]+/g, ' ').replace(/\n{3,}/g, '\n\n');
            const visible = normalize(document.body.innerText);
            const plain = new DOMParser().parseFromString(info.detail || '', 'text/html').body;
            plain.querySelectorAll('script,style').forEach(element => element.remove());
            plain.querySelectorAll('p,div,li,h1,h2,h3,h4,h5,h6,br').forEach(element => element.append(document.createTextNode('\n')));
            const beginning = normalize(plain.textContent).slice(0, 100);
            const rendered = title.length > 0 && normalize(document.title) === normalize(title) &&
                visible.includes(normalize(title)) && beginning.length >= 40 && normalize(body).includes(beginning);
            const positions = data.jobPositionSetting?.jobPositions || [];
            const locations = positions.map(position => position.workspacePlace).filter(Boolean)
                .map(place => [place.place, place.detailPlace].filter(Boolean).join(' ')).filter(Boolean);
            return {title, body, rendered, company: clean(data.groupInfo?.name),
                location: [...new Set(locations)].join(' / '), status: info.status || '', dueDate: info.dueDate || ''};
        }""", id).asObject("오늘의집 상세")
        check(result["rendered"] == true) { "오늘의집 상세 제목·공식 본문이 화면에 렌더링되지 않았습니다. 오류/보안 확인 화면은 저장하지 않습니다." }
        check(result.string("body").length >= 100) { "오늘의집 상세 본문이 비어 있거나 너무 짧습니다." }
        return result
    }
}

/** 오늘의집 공식 팀명과 명확한 직함으로 지원 범위의 후보만 남긴다. 세부 카테고리는 공통 실행 계층에서 판단한다. */
private object BucketplaceCandidateRule {
    private val excludedTeam = setOf("경영지원", "물류/배송", "정보보안")
    private val excludedTitle = Regex("""\b(?:QA|SDET|scientist|analyst|security|accountant|accounting|recruiter)\b|정보보안|정보보호|사이언티스트|애널리스트|데이터\s*분석가|인사\s*담당|회계|재무|세무|총무|입출고|배송\s*운영|물류.*운영|고객\s*(?:지원|상담)|상담\s*매니저|인재\s*(?:풀|pool)|talent\s*pool""", RegexOption.IGNORE_CASE)
    private val softwareTitle = Regex("""(?:software|back[ -]?end|front[ -]?end|full[ -]?stack|android|ios|mobile|data(?: analytics)?|machine learning|ml|ai|ai solutions|cloud|platform|devops|site reliability) (?:engineer|developer)|\bSRE\b|백엔드|프론트엔드|풀스택|(?:안드로이드|서버|웹|앱)\s*개발|(?:데이터|머신러닝|AI)\s*엔지니어|데브옵스""", RegexOption.IGNORE_CASE)
    private val businessTitle = Regex("""product\s*(?:owner|manager|planner)|project\s*manager|\b(?:PM|PO)\b|프로덕트\s*(?:매니저|오너|기획)|(?:서비스|사업)\s*기획|디자이너|디자인|designer|\bdesign\b|마케터|마케팅|marketer|marketing|영업|세일즈|\bsales\b""", RegexOption.IGNORE_CASE)

    fun accepts(title: String, team: String = ""): Boolean = title.isNotBlank() && team !in excludedTeam &&
        !excludedTitle.containsMatchIn(title) && (softwareTitle.containsMatchIn(title) || businessTitle.containsMatchIn(title))
}

private fun Any?.asObject(label: String): Map<*, *> = this as? Map<*, *> ?: error("$label 응답 형식이 변경되었습니다.")
private fun Map<*, *>.string(key: String): String = this[key]?.toString().orEmpty()
