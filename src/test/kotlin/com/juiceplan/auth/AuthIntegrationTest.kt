package com.juiceplan.auth

import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

private const val PASSWORD = "열려라참깨"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = ["app.password=$PASSWORD"])
class AuthIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var authTokens: AuthTokens

    private fun validCookie() = Cookie(AUTH_COOKIE_NAME, authTokens.issue(Instant.now()))

    @Test
    fun `sends an unauthenticated page request to the password page`() {
        mockMvc.perform(get("/schd/day"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/password?next=%2Fschd%2Fday"))
    }

    @Test
    fun `keeps the query string of the page that was asked for`() {
        mockMvc.perform(get("/schd/day?d=2026-09-01"))
            .andExpect(redirectedUrl("/password?next=%2Fschd%2Fday%3Fd%3D2026-09-01"))
    }

    @Test
    fun `answers an unauthenticated api request with 401 json`() {
        // API는 리다이렉트를 받으면 로그인 HTML을 JSON으로 파싱하려 든다
        mockMvc.perform(delete("/api/sources/999"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().string(containsString("인증이 필요합니다")))
    }

    @Test
    fun `serves the password page without a token`() {
        mockMvc.perform(get("/password"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("비밀번호")))
    }

    @Test
    fun `serves static assets without a token`() {
        // 로그인 화면이 style.css 를 쓴다
        mockMvc.perform(get("/css/style.css"))
            .andExpect(status().isOk)
    }

    @Test
    fun `does not open a path that merely starts with an open one`() {
        mockMvc.perform(get("/passwordless"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/password?next=%2Fpasswordless"))
    }

    @Test
    fun `issues an eighteen hour cookie for the right password`() {
        val response = mockMvc.perform(post("/password").param("password", PASSWORD))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
            .andExpect(cookie().httpOnly(AUTH_COOKIE_NAME, true))
            .andExpect(cookie().maxAge(AUTH_COOKIE_NAME, 18 * 60 * 60))
            .andReturn().response

        assertTrue(authTokens.isValid(response.getCookie(AUTH_COOKIE_NAME)?.value, Instant.now()))
    }

    @Test
    fun `lets a request through with a valid cookie`() {
        mockMvc.perform(get("/schd/day").cookie(validCookie()))
            .andExpect(status().isOk)

        // 없는 id의 404는 필터를 지나 앱까지 닿았다는 뜻이다
        mockMvc.perform(delete("/api/sources/999").cookie(validCookie()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `refuses a wrong password without issuing a cookie`() {
        val response = mockMvc.perform(post("/password").param("password", "틀린비밀번호"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().string(containsString("비밀번호가 올바르지 않습니다")))
            .andReturn().response

        assertNull(response.getCookie(AUTH_COOKIE_NAME))
    }

    @Test
    fun `returns to the page that was asked for after logging in`() {
        mockMvc.perform(post("/password").param("password", PASSWORD).param("next", "/schd/plan"))
            .andExpect(redirectedUrl("/schd/plan"))
    }

    @Test
    fun `refuses to bounce off to another site after logging in`() {
        listOf("https://evil.example", "//evil.example", "javascript:alert(1)").forEach { next ->
            mockMvc.perform(post("/password").param("password", PASSWORD).param("next", next))
                .andExpect(redirectedUrl("/"))
        }
    }

    @Test
    fun `sends a request carrying a tampered cookie back to the password page`() {
        val tampered = Cookie(AUTH_COOKIE_NAME, validCookie().value.dropLast(2) + "xy")

        mockMvc.perform(get("/schd/day").cookie(tampered))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/password?next=%2Fschd%2Fday"))
    }

    @Test
    fun `skips the password page when already logged in`() {
        mockMvc.perform(get("/password").param("next", "/schd/plan").cookie(validCookie()))
            .andExpect(redirectedUrl("/schd/plan"))
    }

    @Test
    fun `still allows the root redirect chain to reach the password page`() {
        // 브라우저가 / 로 들어오면 /schd/day 를 거쳐 결국 로그인 화면에 닿아야 한다
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/password?next=%2F"))
    }

    @Test
    fun `leaves the cookie usable over plain http`() {
        // 로컬은 https 가 아니다. Secure 를 무조건 켜면 쿠키가 아예 돌아오지 않는다.
        mockMvc.perform(post("/password").param("password", PASSWORD))
            .andExpect(cookie().secure(AUTH_COOKIE_NAME, false))
    }
}

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthDisabledIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `lets everything through when no password is configured`() {
        mockMvc.perform(get("/schd/day")).andExpect(status().isOk)
        // 401이 아닌 404 — 필터가 아니라 앱이 답했다
        mockMvc.perform(delete("/api/sources/999")).andExpect(status().isNotFound)
    }

    @Test
    fun `has no password page to show`() {
        mockMvc.perform(get("/password"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
    }

    @Test
    fun `hands out no cookie even for a blank password`() {
        val response = mockMvc.perform(post("/password").param("password", ""))
            .andExpect(status().is3xxRedirection)
            .andReturn().response

        assertNull(response.getCookie(AUTH_COOKIE_NAME))
    }
}
