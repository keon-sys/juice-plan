package com.juiceplan.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GoogleMapsLinkParserTest {

    private val parser = GoogleMapsLinkParser()

    @Test
    fun `extracts name and coordinates from a standard place url`() {
        val url = "https://www.google.com/maps/place/Gyeongbokgung+Palace/@37.5796,126.9770,17z/data=xyz"

        val result = parser.parse(url)

        assertEquals("Gyeongbokgung Palace", result?.name)
        assertEquals(37.5796, result?.latitude)
        assertEquals(126.9770, result?.longitude)
    }

    @Test
    fun `returns coordinates even when name segment is absent`() {
        val url = "https://www.google.com/maps/@37.5796,126.9770,17z"

        val result = parser.parse(url)

        assertNull(result?.name)
        assertEquals(37.5796, result?.latitude)
    }

    @Test
    fun `returns null when url has no coordinate pattern`() {
        val url = "https://www.google.com/maps/search/restaurants+near+me"

        val result = parser.parse(url)

        assertNull(result)
    }
}
