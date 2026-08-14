package com.juiceplan.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

const val SESSION_AUTHENTICATED_KEY = "authenticated"

@Component
class AuthInterceptor : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val authenticated = request.session.getAttribute(SESSION_AUTHENTICATED_KEY) == true
        if (!authenticated) {
            if (request.requestURI.startsWith("/api/")) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
            } else {
                response.sendRedirect("/")
            }
            return false
        }
        return true
    }
}
