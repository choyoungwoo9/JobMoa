package com.youngwoo.jobmoa.classification.presentation

import com.youngwoo.jobmoa.classification.application.ReclassificationService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/classifications")
class ClassificationController(private val service: ReclassificationService) {
    @PostMapping("/rebuild") fun rebuild() = service.rebuild()
}
