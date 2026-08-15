package com.juiceplan.shell

import com.juiceplan.source.PlaceType
import com.juiceplan.source.Source
import com.juiceplan.source.SourceRepository
import com.juiceplan.trip.TripRepository
import com.juiceplan.trip.TripService
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShellControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var sourceRepository: SourceRepository
    @Autowired lateinit var tripRepository: TripRepository
    @Autowired lateinit var tripService: TripService

    @BeforeEach
    fun setUp() {
        sourceRepository.deleteAll()
        tripRepository.deleteAll()
    }

    @Test
    fun `renders the shell`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(view().name("shell/index"))
    }

    @Test
    fun `no login is required`() {
        // 인증을 제거했으므로 세션 없이 바로 200이어야 한다
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("장소 추가")))
    }

    @Test
    fun `embeds trip, sources and day notes for the client`() {
        tripService.save(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3))
        sourceRepository.save(
            Source(
                googleMapsUrl = "https://maps.app.goo.gl/x",
                name = "아사쿠사",
                latitude = 35.7148,
                longitude = 139.7967,
                placeType = PlaceType.ATTRACTION,
                durationMinutes = 90,
                reservationRequired = false,
                scheduledDate = LocalDate.of(2026, 9, 1),
                startMinutes = 600
            )
        )

        // Thymeleaf 의 JS 인라이닝은 한글을 \uXXXX 로 이스케이프하므로 이름 대신
        // ASCII로 남는 필드로 확인한다.
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("\"latitude\":35.7148")))
            .andExpect(content().string(containsString("\"startMinutes\":600")))
            .andExpect(content().string(containsString("\"scheduledDate\":\"2026-09-01\"")))
            .andExpect(content().string(containsString("\"startDate\":\"2026-09-01\"")))
    }

    @Test
    fun `renders with a null trip when no trip is set`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("var TRIP = null")))
    }

    @Test
    fun `redirects the old sources path to the shell`() {
        mockMvc.perform(get("/sources"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
    }

    @Test
    fun `redirects the old plan path to the shell`() {
        mockMvc.perform(get("/plan"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
    }

    @Test
    fun `redirects the old day path to the shell`() {
        mockMvc.perform(get("/day"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
    }
}
