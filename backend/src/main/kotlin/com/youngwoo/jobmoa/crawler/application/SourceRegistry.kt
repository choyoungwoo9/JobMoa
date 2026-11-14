package com.youngwoo.jobmoa.crawler.application

import com.youngwoo.jobmoa.crawler.domain.model.SourceDefinition
import com.youngwoo.jobmoa.crawler.domain.port.RecruitmentSite
import org.springframework.stereotype.Component

@Component
class SourceRegistry(adapters: List<RecruitmentSite>) {
    private val adapters = adapters.associateBy { it.definition.id }
    val definitions = listOf(
        SourceDefinition("naver", "네이버", "https://recruit.navercorp.com/rcrt/list.do"),
        SourceDefinition("toss", "토스", "https://toss.im/career/jobs"),
        SourceDefinition("kakao", "카카오", "https://careers.kakao.com/jobs"),
        SourceDefinition("coupang", "쿠팡", "https://www.coupang.jobs/"),
        SourceDefinition("daangn", "당근", "https://careers.daangn.com/jobs/"),
        SourceDefinition("line", "LINE", "https://careers.linecorp.com/ko/jobs/"),
        SourceDefinition("woowa", "우아한형제들", "https://career.woowahan.com/"),
        SourceDefinition("bucketplace", "오늘의집", "https://www.bucketplace.com/careers/"),
    )
    fun supported(id: String) = adapters.containsKey(id)
    fun definition(id: String) = definitions.find { it.id == id } ?: throw IllegalArgumentException("알 수 없는 기업군: $id")
    fun adapter(id: String): RecruitmentSite = adapters[id] ?: throw IllegalArgumentException("아직 구현되지 않은 수집기: $id")
}
