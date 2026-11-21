package com.youngwoo.jobmoa.shared

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFound::class)
    fun notFound(error: ResourceNotFound) = response(HttpStatus.NOT_FOUND, error)
    @ExceptionHandler(CollectionConflict::class)
    fun conflict(error: CollectionConflict) = response(HttpStatus.CONFLICT, error)
    @ExceptionHandler(ServiceUnavailable::class)
    fun unavailable(error: ServiceUnavailable) = response(HttpStatus.SERVICE_UNAVAILABLE, error)
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(error: IllegalArgumentException) = response(HttpStatus.BAD_REQUEST, error)
    private fun response(status: HttpStatus, error: RuntimeException) = ResponseEntity.status(status).body(mapOf("message" to (error.message ?: status.reasonPhrase)))
}
