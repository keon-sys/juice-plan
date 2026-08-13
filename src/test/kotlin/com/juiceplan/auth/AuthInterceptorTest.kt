package com.juiceplan.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthInterceptorTest {

    private val interceptor = AuthInterceptor()

    @Test
    fun `blocks and redirects when session not authenticated`() {
        val session = mockk<HttpSession>()
        every { session.getAttribute(SESSION_AUTHENTICATED_KEY) } returns null
        val request = mockk<HttpServletRequest>()
        every { request.session } returns session
        val response = mockk<HttpServletResponse>(relaxed = true)

        val result = interceptor.preHandle(request, response, Any())

        assertFalse(result)
        verify { response.sendRedirect("/") }
    }

    @Test
    fun `allows when session authenticated`() {
        val session = mockk<HttpSession>()
        every { session.getAttribute(SESSION_AUTHENTICATED_KEY) } returns true
        val request = mockk<HttpServletRequest>()
        every { request.session } returns session
        val response = mockk<HttpServletResponse>(relaxed = true)

        val result = interceptor.preHandle(request, response, Any())

        assertTrue(result)
    }
}
