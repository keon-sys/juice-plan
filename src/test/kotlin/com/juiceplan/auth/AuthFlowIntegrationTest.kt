package com.juiceplan.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var appSettingsRepository: AppSettingsRepository

    @BeforeEach
    fun cleanUp() {
        appSettingsRepository.deleteAll()
    }

    @Test
    fun `shows setup page when no password configured`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(view().name("auth/setup"))
    }

    @Test
    fun `setting initial password authenticates and redirects to sources`() {
        val result = mockMvc.perform(
            post("/setup").param("password", "250707").param("passwordConfirm", "250707")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/sources"))
            .andReturn()

        val session = result.request.session
        assertEquals(true, session?.getAttribute(SESSION_AUTHENTICATED_KEY))
    }

    @Test
    fun `shows login page once password is configured`() {
        appSettingsRepository.save(AppSettings(passwordHash = BCryptPasswordEncoder().encode("250707")))

        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(view().name("auth/login"))
    }

    @Test
    fun `wrong password on login shows error`() {
        appSettingsRepository.save(AppSettings(passwordHash = BCryptPasswordEncoder().encode("250707")))

        mockMvc.perform(post("/login").param("password", "wrong"))
            .andExpect(status().isOk)
            .andExpect(view().name("auth/login"))
    }
}
