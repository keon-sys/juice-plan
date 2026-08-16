package com.juiceplan.config

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private const val DEFAULT_BAD_REQUEST_MESSAGE = "잘못된 요청입니다."
private const val DEFAULT_NOT_FOUND_MESSAGE = "찾을 수 없습니다."

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: DEFAULT_BAD_REQUEST_MESSAGE)))

    /**
     * 서비스 계층은 없는 id에 NoSuchElementException을 던진다. 핸들러가 없으면 500이 나가므로
     * 404로 옮긴다. SourceService.get, ScheduleService.assign/remove가 모두 여기로 모인다.
     */
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(404).body(mapOf("error" to (ex.message ?: DEFAULT_NOT_FOUND_MESSAGE)))
}
