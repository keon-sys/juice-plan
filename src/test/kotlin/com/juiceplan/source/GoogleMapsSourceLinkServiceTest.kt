package com.juiceplan.source

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class GoogleMapsSourceLinkServiceTest {

    private val resolver = mockk<UrlResolver>()
    private val parser = GoogleMapsLinkParser()
    private val service = GoogleMapsSourceLinkService(resolver, parser)

    @Test
    fun `returns success with parsed place when resolution and parsing succeed`() {
        every { resolver.resolve("https://maps.app.goo.gl/abc") } returns
            "https://www.google.com/maps/place/Gyeongbokgung+Palace/@37.5796,126.9770,17z/data=xyz"

        val result = service.parseLink("https://maps.app.goo.gl/abc")

        assertTrue(result.success)
        assertEquals(37.5796, result.place?.latitude)
    }

    @Test
    fun `returns failure when resolver throws`() {
        every { resolver.resolve(any()) } throws IOException("network error")

        val result = service.parseLink("https://maps.app.goo.gl/broken")

        assertFalse(result.success)
    }

    @Test
    fun `returns failure when resolved url has no coordinates`() {
        every { resolver.resolve(any()) } returns "https://www.google.com/maps/search/restaurants"

        val result = service.parseLink("https://maps.app.goo.gl/search-link")

        assertFalse(result.success)
    }

    @Test
    fun `returns failure instead of propagating when the parser throws on a malformed percent escape`() {
        every { resolver.resolve(any()) } returns
            "https://www.google.com/maps/place/Bad%ZZName/@37.5796,126.9770,17z/"

        val result = service.parseLink("https://maps.app.goo.gl/bad-escape")

        assertFalse(result.success)
    }
}
