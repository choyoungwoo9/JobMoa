package com.youngwoo.jobmoa.crawler.infrastructure.site.yeogi

import com.youngwoo.jobmoa.crawler.domain.model.SourceDefinition
import com.youngwoo.jobmoa.crawler.infrastructure.site.greeting.GreetingEngineeringSite
import org.springframework.stereotype.Component

/** 여기어때 공식 Greeting 채용 포털에서 기술 직군의 명확한 개발 공고를 수집한다. */
@Component
class YeogiRecruitmentSite : GreetingEngineeringSite(
    definition = SourceDefinition("yeogi", "여기어때", "https://gccompany.career.greetinghr.com/ko/apply"),
    acceptedHosts = setOf("gccompany.career.greetinghr.com"),
    roleGroups = setOf("기술"),
    roleNames = emptySet(),
)
