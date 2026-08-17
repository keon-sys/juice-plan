package com.juiceplan.auth

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class AuthTokensTest {

    private val now = Instant.parse("2026-08-17T09:00:00Z")
    private val tokens = AuthTokens("열려라참깨")

    @Test
    fun `accepts a token it just issued`() {
        assertTrue(tokens.isValid(tokens.issue(now), now))
    }

    @Test
    fun `accepts a token in the seventeenth hour of its life`() {
        val token = tokens.issue(now)

        assertTrue(tokens.isValid(token, now + Duration.ofHours(17)))
    }

    @Test
    fun `rejects a token past its eighteen hour lifetime`() {
        val token = tokens.issue(now)

        assertFalse(tokens.isValid(token, now + Duration.ofHours(18) + Duration.ofSeconds(1)))
    }

    @Test
    fun `rejects a token whose expiry was pushed out`() {
        // 서명이 만료시각을 덮고 있어야 한다. 아니면 만료된 토큰의 앞부분만 고쳐 되살릴 수 있다.
        val token = tokens.issue(now)
        val forged = "${now.plus(Duration.ofDays(30)).epochSecond}.${token.substringAfter('.')}"

        assertFalse(tokens.isValid(forged, now))
    }

    @Test
    fun `rejects a token whose signature was tampered with`() {
        val token = tokens.issue(now)
        val tampered = token.dropLast(1) + if (token.last() == 'A') 'B' else 'A'

        assertFalse(tokens.isValid(tampered, now))
    }

    @Test
    fun `rejects a token issued under a different password`() {
        val issuedElsewhere = AuthTokens("다른비밀번호").issue(now)

        assertFalse(tokens.isValid(issuedElsewhere, now))
    }

    @Test
    fun `rejects tokens that are missing or malformed`() {
        listOf(null, "", ".", "abc", "abc.def", "${now.epochSecond}.").forEach {
            assertFalse(tokens.isValid(it, now), "should have rejected: $it")
        }
    }

    @Test
    fun `matches the configured password`() {
        assertTrue(tokens.matches("열려라참깨"))
    }

    @Test
    fun `does not match a wrong password`() {
        assertFalse(tokens.matches("열려라깨참"))
    }

    @Test
    fun `is enabled when a password is configured`() {
        assertTrue(tokens.enabled)
    }

    @Test
    fun `is disabled when no password is configured`() {
        // APP_PASSWORD 미설정이면 인증 기능 자체가 꺼진다
        listOf("", "   ").forEach {
            assertFalse(AuthTokens(it).enabled, "should have been disabled for: '$it'")
        }
    }

    @Test
    fun `matches nothing while disabled`() {
        // 꺼져 있을 때 빈 비밀번호로 로그인이 되어버리면 안 된다
        listOf("", "   ", "아무거나").forEach {
            assertFalse(AuthTokens("").matches(it), "should have rejected: '$it'")
        }
    }
}
