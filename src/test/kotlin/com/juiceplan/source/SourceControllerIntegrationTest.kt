package com.juiceplan.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SourceControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var sourceRepository: SourceRepository

    @BeforeEach
    fun setUp() {
        sourceRepository.deleteAll()
    }

    private fun body(
        name: String = "경복궁",
        placeType: String = "ATTRACTION",
        reservationRequired: Boolean = false,
        reservationDeadline: String? = null
    ) = """
        {
          "googleMapsUrl": "https://maps.app.goo.gl/abc",
          "name": "$name",
          "latitude": 37.5796,
          "longitude": 126.9770,
          "placeType": "$placeType",
          "durationHours": 1,
          "durationMinutesPart": 30,
          "reservationRequired": $reservationRequired,
          "reservationDeadline": ${if (reservationDeadline == null) "null" else "\"$reservationDeadline\""},
          "memo": null
        }
    """.trimIndent()

    private fun newSource() = sourceRepository.save(
        Source(
            googleMapsUrl = "https://maps.app.goo.gl/x",
            name = "기존",
            latitude = 37.0,
            longitude = 127.0,
            placeType = PlaceType.ATTRACTION,
            durationMinutes = 60,
            reservationRequired = false
        )
    )

    @Test
    fun `creates a source and returns it with a generated id`() {
        mockMvc.perform(
            post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(body())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.name").value("경복궁"))
            // 1시간 30분 -> 90분
            .andExpect(jsonPath("$.durationMinutes").value(90))
            .andExpect(jsonPath("$.scheduledDate").doesNotExist())

        assertEquals(1, sourceRepository.count())
    }

    @Test
    fun `rejects a reservation without a deadline`() {
        mockMvc.perform(
            post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                .content(body(reservationRequired = true))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("예약이 필요한 경우 예약 마감일을 입력해야 합니다."))

        assertEquals(0, sourceRepository.count())
    }

    @Test
    fun `accepts a reservation with a deadline`() {
        mockMvc.perform(
            post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                .content(body(reservationRequired = true, reservationDeadline = "2026-08-25"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reservationRequired").value(true))
            .andExpect(jsonPath("$.reservationDeadline").value("2026-08-25"))
    }

    @Test
    fun `updates a source and returns the new state`() {
        val existing = newSource()

        mockMvc.perform(
            put("/api/sources/${existing.id}").contentType(MediaType.APPLICATION_JSON)
                .content(body(name = "수정된 이름", placeType = "RESTAURANT"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(existing.id))
            .andExpect(jsonPath("$.name").value("수정된 이름"))
            .andExpect(jsonPath("$.placeType").value("RESTAURANT"))
    }

    @Test
    fun `deletes a source`() {
        val existing = newSource()

        mockMvc.perform(delete("/api/sources/${existing.id}"))
            .andExpect(status().isOk)

        assertTrue(sourceRepository.findById(existing.id).isEmpty)
    }

    @Test
    fun `returns 404 when updating a source that does not exist`() {
        mockMvc.perform(
            put("/api/sources/9999").contentType(MediaType.APPLICATION_JSON).content(body())
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `returns 404 when deleting a source that does not exist`() {
        mockMvc.perform(delete("/api/sources/9999"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `keeps the schedule assignment when a source is edited`() {
        val existing = newSource()
        existing.scheduledDate = LocalDate.of(2026, 9, 1)
        existing.startMinutes = 600
        sourceRepository.save(existing)

        mockMvc.perform(
            put("/api/sources/${existing.id}").contentType(MediaType.APPLICATION_JSON)
                .content(body(name = "이름만 변경"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.scheduledDate").value("2026-09-01"))
            .andExpect(jsonPath("$.startMinutes").value(600))
    }
}
