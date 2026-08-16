package com.juiceplan.source

import org.springframework.stereotype.Component
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ParsedPlace(val name: String?, val latitude: Double, val longitude: Double)

@Component
class GoogleMapsLinkParser {

    private val coordPattern = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+),\d+(?:\.\d+)?z""")
    private val namePattern = Regex("""/place/([^/@]+)""")

    fun parse(resolvedUrl: String): ParsedPlace? {
        val coordMatch = coordPattern.find(resolvedUrl) ?: return null
        val latitude = coordMatch.groupValues[1].toDouble()
        val longitude = coordMatch.groupValues[2].toDouble()

        val name = namePattern.find(resolvedUrl)?.groupValues?.get(1)?.let {
            URLDecoder.decode(it, StandardCharsets.UTF_8).replace('+', ' ')
        }

        return ParsedPlace(name = name, latitude = latitude, longitude = longitude)
    }
}
