package com.juiceplan.plan

import com.juiceplan.auth.SESSION_AUTHENTICATED_KEY
import com.juiceplan.daynote.DayNoteRepository
import com.juiceplan.daynote.DayNote
import com.juiceplan.trip.Trip
import com.juiceplan.trip.TripRepository
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var tripRepository: TripRepository
    @Autowired lateinit var dayNoteRepository: DayNoteRepository

    private lateinit var session: MockHttpSession

    @BeforeEach
    fun setUp() {
        tripRepository.deleteAll()
        dayNoteRepository.deleteAll()
        session = MockHttpSession()
        session.setAttribute(SESSION_AUTHENTICATED_KEY, true)
    }

    @Test
    fun `shows guidance message when trip is not configured`() {
        mockMvc.perform(get("/plan").session(session))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("먼저 여행 기간을 설정해주세요")))
    }

    @Test
    fun `renders plan page with embedded sources and day notes when trip exists`() {
        tripRepository.save(Trip(startDate = LocalDate.of(2026, 9, 1), endDate = LocalDate.of(2026, 9, 5)))
        dayNoteRepository.save(DayNote(date = LocalDate.of(2026, 9, 1), memo = "오전엔 우천 예보"))

        mockMvc.perform(get("/plan").session(session))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("plan-app")))
            .andExpect(content().string(containsString("var DAY_NOTES")))
            // Thymeleaf's default th:inline="javascript" serializer safely unicode-escapes
            // non-ASCII characters inside inline <script> blocks (part of its script-context
            // escaping defenses), so the memo appears as \uXXXX sequences rather than raw
            // Korean text. This asserts the exact escaped form Jackson produces for
            // "오전엔 우천 예보", proving the day note's actual content round-tripped into the
            // page (not an empty/placeholder DAY_NOTES) without weakening that escaping.
            .andExpect(content().string(containsString("\\uC624\\uC804\\uC5D4 \\uC6B0\\uCC9C \\uC608\\uBCF4")))
    }

    @Test
    fun `unauthenticated access redirects to gate`() {
        mockMvc.perform(get("/plan"))
            .andExpect(status().is3xxRedirection)
    }
}
