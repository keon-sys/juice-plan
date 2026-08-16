package com.juiceplan.schedule

import com.juiceplan.source.PlaceType
import com.juiceplan.source.Source
import com.juiceplan.source.SourceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScheduleControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var sourceRepository: SourceRepository

    @BeforeEach
    fun setUp() {
        sourceRepository.deleteAll()
    }

    private fun newSource(name: String) = sourceRepository.save(
        Source(
            googleMapsUrl = "https://maps.app.goo.gl/x",
            name = name,
            latitude = 37.0,
            longitude = 127.0,
            placeType = PlaceType.ATTRACTION,
            durationMinutes = 60,
            reservationRequired = false
        )
    )

    @Test
    fun `assigns a source to a date and time`() {
        val a = newSource("A")

        mockMvc.perform(
            put("/api/schedule/${a.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"date":"2026-09-01","startMinutes":600}""")
        ).andExpect(status().isOk)

        val reloaded = sourceRepository.findById(a.id).get()
        assertEquals(LocalDate.of(2026, 9, 1), reloaded.scheduledDate)
        assertEquals(600, reloaded.startMinutes)
    }

    @Test
    fun `rejects a time outside the placeable window with 400`() {
        val a = newSource("A")

        mockMvc.perform(
            put("/api/schedule/${a.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"date":"2026-09-01","startMinutes":120}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects a time that is not on a 30 minute slot with 400`() {
        val a = newSource("A")

        mockMvc.perform(
            put("/api/schedule/${a.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"date":"2026-09-01","startMinutes":615}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `removes a source from the schedule`() {
        val a = newSource("A")
        a.scheduledDate = LocalDate.of(2026, 9, 1)
        a.startMinutes = 600
        sourceRepository.save(a)

        mockMvc.perform(delete("/api/schedule/${a.id}"))
            .andExpect(status().isOk)

        val reloaded = sourceRepository.findById(a.id).get()
        assertNull(reloaded.scheduledDate)
        assertNull(reloaded.startMinutes)
    }

    @Test
    fun `returns 404 when assigning a source that does not exist`() {
        mockMvc.perform(
            put("/api/schedule/9999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"date":"2026-09-01","startMinutes":600}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `returns 404 when removing a source that does not exist`() {
        mockMvc.perform(delete("/api/schedule/9999"))
            .andExpect(status().isNotFound)
    }
}
