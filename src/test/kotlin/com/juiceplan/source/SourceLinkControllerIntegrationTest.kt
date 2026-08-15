package com.juiceplan.source

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
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
import java.io.IOException

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SourceLinkControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var urlResolver: UrlResolver

    @Test
    fun `parse-link returns parsed place on success`() {
        every { urlResolver.resolve(any()) } returns
            "https://www.google.com/maps/place/Gyeongbokgung+Palace/@37.5796,126.9770,17z/data=xyz"

        mockMvc.perform(
            post("/api/sources/parse-link")
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
            post("/api/sources/parse-link")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"https://maps.app.goo.gl/broken"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
    }
}
