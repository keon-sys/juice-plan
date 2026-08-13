package com.juiceplan.auth

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.view.RedirectView

@Controller
class AuthController(private val authService: AuthService) {

    @GetMapping("/")
    fun gate(request: HttpServletRequest, model: Model): Any {
        if (request.session.getAttribute(SESSION_AUTHENTICATED_KEY) == true) {
            return RedirectView("/sources")
        }
        return if (authService.isConfigured()) "auth/login" else "auth/setup"
    }

    @PostMapping("/setup")
    fun setup(
        @RequestParam password: String,
        @RequestParam passwordConfirm: String,
        request: HttpServletRequest,
        model: Model
    ): Any {
        if (authService.isConfigured()) {
            return RedirectView("/")
        }
        if (password.isBlank() || password != passwordConfirm) {
            model.addAttribute("error", "비밀번호가 일치하지 않습니다.")
            return "auth/setup"
        }
        authService.setInitialPassword(password)
        request.session.setAttribute(SESSION_AUTHENTICATED_KEY, true)
        return RedirectView("/sources")
    }

    @PostMapping("/login")
    fun login(
        @RequestParam password: String,
        request: HttpServletRequest,
        model: Model
    ): Any {
        if (!authService.verify(password)) {
            model.addAttribute("error", "비밀번호가 올바르지 않습니다.")
            return "auth/login"
        }
        request.session.setAttribute(SESSION_AUTHENTICATED_KEY, true)
        return RedirectView("/sources")
    }
}
