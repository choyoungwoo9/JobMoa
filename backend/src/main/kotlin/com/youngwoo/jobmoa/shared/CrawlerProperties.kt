package com.youngwoo.jobmoa.shared

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import java.time.Duration

@ConfigurationProperties("jobmoa.crawler")
data class CrawlerProperties(
    val workers: Int = 3,
    val siteConcurrency: Int = 1,
    val siteOverrides: Map<String, Int> = emptyMap(),
    val minimumNavigationInterval: Duration = Duration.ofMillis(700),
    val minimumNavigationIntervalOverrides: Map<String, Duration> = emptyMap(),
    val leaseDuration: Duration = Duration.ofMinutes(2),
    val navigationTimeout: Duration = Duration.ofSeconds(30),
    val browserChannel: String = "",
    val headless: Boolean = true,
    val storageDirectory: Path = Path.of("storage"),
) {
    init {
        require(workers in 1..16) { "작업자 수는 1~16 사이여야 합니다." }
        require(siteConcurrency > 0 && siteOverrides.values.all { it > 0 })
        require(leaseDuration >= navigationTimeout.multipliedBy(2))
        require(!minimumNavigationInterval.isNegative)
        require(minimumNavigationIntervalOverrides.values.all { !it.isNegative })
    }
}
