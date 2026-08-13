package com.juiceplan.daynote

import com.juiceplan.auth.SESSION_AUTHENTICATED_KEY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DayNoteControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var dayNoteRepository: DayNoteRepository

    private lateinit var session: MockHttpSession

    @BeforeEach
    fun setUp() {
        dayNoteRepository.deleteAll()
        session = MockHttpSession()
        session.setAttribute(SESSION_AUTHENTICATED_KEY, true)
    }

    @Test
    fun `saves a memo for the given date`() {
        mockMvc.perform(
            post("/api/day-notes/2026-09-01").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memo":"오전엔 우천 예보"}""")
        ).andExpect(status().isOk)

        val note = dayNoteRepository.findByDate(LocalDate.of(2026, 9, 1))
        assertEquals("오전엔 우천 예보", note?.memo)
    }

    @Test
    fun `saving a blank memo deletes the existing note`() {
        dayNoteRepository.save(DayNote(date = LocalDate.of(2026, 9, 1), memo = "지울 메모"))

        mockMvc.perform(
            post("/api/day-notes/2026-09-01").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memo":""}""")
        ).andExpect(status().isOk)

        assertNull(dayNoteRepository.findByDate(LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun `unauthenticated request is blocked`() {
        mockMvc.perform(
            post("/api/day-notes/2026-09-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memo":"test"}""")
        ).andExpect(status().is3xxRedirection)
    }
}
