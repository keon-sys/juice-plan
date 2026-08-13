package com.juiceplan.auth

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val appSettingsRepository: AppSettingsRepository
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    fun isConfigured(): Boolean = appSettingsRepository.count() > 0

    fun setInitialPassword(rawPassword: String) {
        check(!isConfigured()) { "Password already configured" }
        val hash = passwordEncoder.encode(rawPassword)
        appSettingsRepository.save(AppSettings(passwordHash = hash))
    }

    fun verify(rawPassword: String): Boolean {
        val settings = appSettingsRepository.findAll().firstOrNull() ?: return false
        return passwordEncoder.matches(rawPassword, settings.passwordHash)
    }
}
