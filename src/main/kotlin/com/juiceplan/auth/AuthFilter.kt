package com.juiceplan.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

const val AUTH_COOKIE_NAME = "jp_auth"
const val PASSWORD_PATH = "/password"

/** 로그인 화면 자체와 그 화면이 쓰는 정적 파일. 여기엔 여행 데이터가 없다. */
private val OPEN_PATHS = setOf(PASSWORD_PATH, "/favicon.ico")
private val OPEN_PREFIXES = listOf("/css/", "/js/")

private const val UNAUTHORIZED_BODY = """{"error":"인증이 필요합니다."}"""

/**
 * APP_PASSWORD 가 설정돼 있으면 유효한 토큰 쿠키를 가진 요청만 통과시킨다.
 *
 * 페이지 요청은 로그인 화면으로 보내고 API 요청은 401 JSON 으로 끊는다. API 에 리다이렉트를
 * 돌려주면 프런트가 로그인 HTML 을 JSON 으로 파싱하려 들기 때문이다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthFilter(private val tokens: AuthTokens) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!tokens.enabled || isOpen(request.requestURI) || hasValidToken(request)) {
            filterChain.doFilter(request, response)
            return
        }

        if (request.requestURI.startsWith("/api/")) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write(UNAUTHORIZED_BODY)
        } else {
            response.sendRedirect("$PASSWORD_PATH?next=${encode(fullPath(request))}")
        }
    }

    private fun isOpen(uri: String) = uri in OPEN_PATHS || OPEN_PREFIXES.any { uri.startsWith(it) }

    private fun hasValidToken(request: HttpServletRequest) =
        tokens.isValid(request.cookies?.firstOrNull { it.name == AUTH_COOKIE_NAME }?.value, Instant.now())

    private fun fullPath(request: HttpServletRequest) =
        request.requestURI + (request.queryString?.let { "?$it" } ?: "")

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
