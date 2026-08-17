package com.juiceplan.auth

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.view.RedirectView
import java.time.Instant

private const val VIEW = "auth/password"
private const val WRONG_PASSWORD_MESSAGE = "비밀번호가 올바르지 않습니다."

/**
 * 비밀번호 하나로 앱 전체를 여는 로그인 화면. [AuthFilter] 가 인증되지 않은 페이지 요청을
 * 원래 주소와 함께 여기로 보낸다.
 */
@Controller
class PasswordController(private val tokens: AuthTokens) {

    @GetMapping(PASSWORD_PATH)
    fun form(
        @RequestParam(required = false) next: String?,
        request: HttpServletRequest,
        model: Model
    ): Any {
        if (!tokens.enabled) return RedirectView("/")
        if (tokens.isValid(cookieValue(request), Instant.now())) return RedirectView(safeNext(next))

        model.addAttribute("next", next ?: "")
        return VIEW
    }

    @PostMapping(PASSWORD_PATH)
    fun submit(
        @RequestParam password: String,
        @RequestParam(required = false) next: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
        model: Model
    ): Any {
        if (!tokens.enabled) return RedirectView("/")

        if (!tokens.matches(password)) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            model.addAttribute("next", next ?: "")
            model.addAttribute("error", WRONG_PASSWORD_MESSAGE)
            return VIEW
        }

        response.addCookie(authCookie(tokens.issue(Instant.now()), request.isSecure))
        return RedirectView(safeNext(next))
    }

    private fun authCookie(token: String, secure: Boolean) = Cookie(AUTH_COOKIE_NAME, token).apply {
        isHttpOnly = true
        path = "/"
        maxAge = TOKEN_LIFETIME.seconds.toInt()
        // 로컬은 http 라 Secure 를 무조건 켜면 쿠키가 아예 돌아오지 않는다.
        this.secure = secure
        setAttribute("SameSite", "Lax")
    }

    /**
     * 로그인 후 돌아갈 곳. 필터가 붙여준 값이지만 주소창으로도 들어올 수 있으므로
     * 이 사이트 안의 경로만 허용한다. `//`와 `/\`는 브라우저가 다른 호스트로 읽는다.
     */
    private fun safeNext(next: String?): String =
        if (next != null && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\")) next
        else "/"

    private fun cookieValue(request: HttpServletRequest) =
        request.cookies?.firstOrNull { it.name == AUTH_COOKIE_NAME }?.value
}
