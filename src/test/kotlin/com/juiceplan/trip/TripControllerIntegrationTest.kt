package com.juiceplan.trip

import com.juiceplan.auth.SESSION_AUTHENTICATED_KEY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TripControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var tripRepository: TripRepository

    @Test
    fun `saving trip dates redirects to sources`() {
        val session = MockHttpSession()
        session.setAttribute(SESSION_AUTHENTICATED_KEY, true)

        mockMvc.perform(
            post("/trip").session(session)
                .param("startDate", "2026-09-01")
                .param("endDate", "2026-09-05")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/sources"))

        val trip = tripRepository.findAll().first()
        assertEquals(LocalDate.of(2026, 9, 1), trip.startDate)
    }

    @Test
    fun `unauthenticated request is redirected to gate`() {
        mockMvc.perform(
            post("/trip")
                .param("startDate", "2026-09-01")
                .param("endDate", "2026-09-05")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
    }

    @Test
    fun `saving a trip with start date after end date redirects back with a flash error instead of 500`() {
        val session = MockHttpSession()
        session.setAttribute(SESSION_AUTHENTICATED_KEY, true)

        mockMvc.perform(
            post("/trip").session(session)
                .param("startDate", "2026-09-05")
                .param("endDate", "2026-09-01")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/sources"))
            .andExpect(flash().attributeExists("error"))
    }
}
