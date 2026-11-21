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
        SourceDefinition("socar", "쏘카", "https://www.socarcorp.kr/careers/jobs"),
        SourceDefinition("kurly", "컬리", "https://kurly.career.greetinghr.com/ko/recruiting"),
        SourceDefinition("musinsa", "무신사", "https://www.musinsacareers.com/ko/home"),
        SourceDefinition("yanolja", "야놀자", "https://yanolja.wd102.myworkdayjobs.com/External_Yanolja"),
        SourceDefinition("ridi", "리디", "https://ridi.recruit.roundhr.com/"),
        SourceDefinition("channel", "채널코퍼레이션", "https://channel.io/kr/careers"),
        SourceDefinition("yeogi", "여기어때", "https://internship.yeogi.com/"),
        SourceDefinition("banksalad", "뱅크샐러드", "https://corp.banksalad.com/jobs/"),
        SourceDefinition("zigbang", "직방", "https://zigbang.career.greetinghr.com/ko/home"),
    )
    fun supported(id: String) = adapters.containsKey(id)
    fun definition(id: String) = definitions.find { it.id == id } ?: throw IllegalArgumentException("알 수 없는 기업군: $id")
    fun adapter(id: String): RecruitmentSite = adapters[id] ?: throw IllegalArgumentException("아직 구현되지 않은 수집기: $id")
}
