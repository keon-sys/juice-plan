package com.juiceplan.auth

import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 로그인한 시점부터 이만큼. 활동해도 늘어나지 않는 절대 만료다. */
val TOKEN_LIFETIME: Duration = Duration.ofHours(18)

private const val HMAC_ALGORITHM = "HmacSHA256"

/**
 * `<만료 epoch초>.<서명>` 형태의 토큰을 발급하고 검증한다.
 *
 * 서명 키가 비밀번호 자체이므로 서버는 발급한 토큰을 하나도 기억하지 않는다. 앱을 재시작해도
 * 로그인이 유지되고, 비밀번호를 바꾸면 이미 나간 토큰이 전부 한꺼번에 무효가 된다.
 */
class AuthTokens(private val password: String) {

    /** APP_PASSWORD 가 비어 있으면 인증 기능 자체가 꺼진다. */
    val enabled: Boolean get() = password.isNotBlank()

    fun matches(candidate: String): Boolean = enabled && constantTimeEquals(candidate, password)

    fun issue(now: Instant): String {
        val expiresAt = now.plus(TOKEN_LIFETIME).epochSecond
        return "$expiresAt.${sign(expiresAt)}"
    }

    fun isValid(token: String?, now: Instant): Boolean {
        if (token == null) return false
        val expiresAt = token.substringBefore('.', "").toLongOrNull() ?: return false
        // 만료시각까지 서명이 덮으므로, 만료된 토큰의 앞부분만 고쳐서는 되살릴 수 없다.
        if (!constantTimeEquals(token.substringAfter('.', ""), sign(expiresAt))) return false
        return now.epochSecond <= expiresAt
    }

    private fun sign(expiresAt: Long): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(password.toByteArray(), HMAC_ALGORITHM))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(expiresAt.toString().toByteArray()))
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
