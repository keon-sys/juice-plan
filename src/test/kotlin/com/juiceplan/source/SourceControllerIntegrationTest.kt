package com.juiceplan.source

import com.juiceplan.auth.SESSION_AUTHENTICATED_KEY
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.IOException

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SourceControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var sourceRepository: SourceRepository

    @MockkBean
    lateinit var urlResolver: UrlResolver

    private lateinit var session: MockHttpSession

    @BeforeEach
    fun setUp() {
        sourceRepository.deleteAll()
        session = MockHttpSession()
        session.setAttribute(SESSION_AUTHENTICATED_KEY, true)
    }

    @Test
    fun `creating a source persists it with memo and redirects to sources list`() {
        mockMvc.perform(
            post("/sources").session(session)
                .param("googleMapsUrl", "https://maps.app.goo.gl/abc")
                .param("name", "경복궁")
                .param("latitude", "37.5796")
                .param("longitude", "126.9770")
                .param("placeType", "ATTRACTION")
                .param("durationHours", "1")
                .param("durationMinutesPart", "30")
                .param("reservationRequired", "false")
                .param("memo", "창가 자리 요청")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/sources"))

        val saved = sourceRepository.findAll()
        assertEquals(1, saved.size)
        assertEquals(90, saved[0].durationMinutes)
        assertEquals("창가 자리 요청", saved[0].memo)
    }

    @Test
    fun `creating a source without reservationRequired param defaults to false like an unchecked checkbox`() {
        mockMvc.perform(
            post("/sources").session(session)
                .param("googleMapsUrl", "https://maps.app.goo.gl/abc")
                .param("name", "경복궁")
                .param("latitude", "37.5796")
                .param("longitude", "126.9770")
                .param("placeType", "ATTRACTION")
                .param("durationHours", "1")
                .param("durationMinutesPart", "30")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/sources"))

        val saved = sourceRepository.findAll()
        assertEquals(1, saved.size)
        assertEquals(false, saved[0].reservationRequired)
    }

    @Test
    fun `deleting a source removes it`() {
        val source = sourceRepository.save(
            Source(
                googleMapsUrl = "https://maps.app.goo.gl/abc",
                name = "경복궁",
                latitude = 37.5796,
                longitude = 126.9770,
                placeType = PlaceType.ATTRACTION,
                durationMinutes = 90,
                reservationRequired = false
            )
        )

        mockMvc.perform(delete("/sources/${source.id}").session(session))
            .andExpect(status().is3xxRedirection)

        assertTrue(sourceRepository.findAll().isEmpty())
    }

    @Test
    fun `parse-link returns parsed place on success`() {
        every { urlResolver.resolve(any()) } returns
            "https://www.google.com/maps/place/Gyeongbokgung+Palace/@37.5796,126.9770,17z/data=xyz"

        mockMvc.perform(
            post("/api/sources/parse-link").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"https://maps.app.goo.gl/abc"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.place.latitude").value(37.5796))
    }

    @Test
    fun `parse-link returns failure when resolver throws`() {
        every { urlResolver.resolve(any()) } throws IOException("boom")

        mockMvc.perform(
            post("/api/sources/parse-link").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"https://maps.app.goo.gl/broken"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `sources page lists saved sources`() {
        sourceRepository.save(
            Source(
                googleMapsUrl = "https://maps.app.goo.gl/abc",
                name = "경복궁",
                latitude = 37.5796,
                longitude = 126.9770,
                placeType = PlaceType.ATTRACTION,
                durationMinutes = 90,
                reservationRequired = false
            )
        )

        mockMvc.perform(get("/sources").session(session))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("경복궁")))
    }

    @Test
    fun `unauthenticated access to sources page redirects to gate`() {
        mockMvc.perform(get("/sources"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
    }
}
