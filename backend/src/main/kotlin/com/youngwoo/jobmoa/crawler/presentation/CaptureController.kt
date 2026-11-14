package com.youngwoo.jobmoa.crawler.presentation

import com.youngwoo.jobmoa.crawler.infrastructure.storage.CaptureStorage
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Duration

@RestController
class CaptureController(private val captures: CaptureStorage) {
    @GetMapping("/api/captures/{name}", produces = [MediaType.IMAGE_PNG_VALUE])
    fun capture(@PathVariable name: String) = ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
        .body(captures.read(name))
}
