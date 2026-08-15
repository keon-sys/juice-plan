package com.juiceplan.dayview

import com.juiceplan.auth.SESSION_AUTHENTICATED_KEY
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DayViewControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun authedSession() = MockHttpSession().apply {
        setAttribute(SESSION_AUTHENTICATED_KEY, true)
    }

    @Test
    fun `renders the day view for an authenticated user`() {
        mockMvc.perform(get("/day").session(authedSession()))
            .andExpect(status().isOk)
            .andExpect(view().name("dayview/index"))
    }

    @Test
    fun `redirects an unauthenticated user to the login page`() {
        mockMvc.perform(get("/day"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
    }
}
