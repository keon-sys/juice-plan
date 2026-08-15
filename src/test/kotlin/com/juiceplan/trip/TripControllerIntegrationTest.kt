package com.juiceplan.trip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TripControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var tripRepository: TripRepository

    @BeforeEach
    fun setUp() {
        tripRepository.deleteAll()
    }

    private fun save(startDate: String, endDate: String) = mockMvc.perform(
        post("/api/trip")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"startDate":"$startDate","endDate":"$endDate"}""")
    )

    @Test
    fun `saves trip dates and returns the trip`() {
        save("2026-09-01", "2026-09-05")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.startDate").value("2026-09-01"))
            .andExpect(jsonPath("$.endDate").value("2026-09-05"))

        val trip = tripRepository.findAll().first()
        assertEquals(LocalDate.of(2026, 9, 1), trip.startDate)
    }

    @Test
    fun `saving twice updates the existing trip instead of creating another`() {
        save("2026-09-01", "2026-09-05").andExpect(status().isOk)
        save("2026-10-01", "2026-10-03").andExpect(status().isOk)

        assertEquals(1, tripRepository.count())
        val trip = tripRepository.findAll().first()
        assertEquals(LocalDate.of(2026, 10, 1), trip.startDate)
        assertEquals(LocalDate.of(2026, 10, 3), trip.endDate)
    }

    @Test
    fun `rejects a start date after the end date with 400`() {
        save("2026-09-05", "2026-09-01")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("시작일은 종료일보다 늦을 수 없습니다."))

        assertEquals(0, tripRepository.count())
    }
}
