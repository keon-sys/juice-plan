package com.juiceplan.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AuthServiceTest {

    private val repository = mockk<AppSettingsRepository>()
    private val authService = AuthService(repository)

    @Test
    fun `isConfigured is false when no settings row exists`() {
        every { repository.count() } returns 0
        assertFalse(authService.isConfigured())
    }

    @Test
    fun `isConfigured is true when a settings row exists`() {
        every { repository.count() } returns 1
        assertTrue(authService.isConfigured())
    }

    @Test
    fun `setInitialPassword saves a bcrypt hash`() {
        every { repository.count() } returns 0
        every { repository.save(any()) } answers { firstArg() }

        authService.setInitialPassword("250707")

        verify {
            repository.save(match { it.passwordHash.startsWith("$2a$") || it.passwordHash.startsWith("$2b$") })
        }
    }

    @Test
    fun `setInitialPassword throws if already configured`() {
        every { repository.count() } returns 1
        assertThrows<IllegalStateException> {
            authService.setInitialPassword("250707")
        }
    }

    @Test
    fun `verify returns true for correct password`() {
        val encoder = BCryptPasswordEncoder()
        val settings = AppSettings(id = 1, passwordHash = encoder.encode("250707"))
        every { repository.findAll() } returns listOf(settings)

        assertTrue(authService.verify("250707"))
    }

    @Test
    fun `verify returns false for incorrect password`() {
        val encoder = BCryptPasswordEncoder()
        val settings = AppSettings(id = 1, passwordHash = encoder.encode("250707"))
        every { repository.findAll() } returns listOf(settings)

        assertFalse(authService.verify("wrong"))
    }

    @Test
    fun `verify returns false when unconfigured`() {
        every { repository.findAll() } returns emptyList()
        assertFalse(authService.verify("anything"))
    }
}
