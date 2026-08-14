package com.juiceplan.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.springframework.web.servlet.view.RedirectView

private const val DEFAULT_ERROR_MESSAGE = "잘못된 요청입니다."

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest,
        redirectAttributes: RedirectAttributes
    ): Any {
        val message = ex.message ?: DEFAULT_ERROR_MESSAGE
        if (request.requestURI.startsWith("/api/")) {
            return ResponseEntity.badRequest().body(mapOf("error" to message))
        }
        redirectAttributes.addFlashAttribute("error", message)
        return RedirectView("/sources")
    }
}
