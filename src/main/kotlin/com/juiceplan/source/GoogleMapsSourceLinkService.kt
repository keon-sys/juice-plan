package com.juiceplan.source

import org.springframework.stereotype.Service

data class LinkParseResult(val success: Boolean, val place: ParsedPlace? = null)

@Service
class GoogleMapsSourceLinkService(
    private val urlResolver: UrlResolver,
    private val linkParser: GoogleMapsLinkParser
) {
    fun parseLink(shortUrl: String): LinkParseResult {
        val place = try {
            val resolved = urlResolver.resolve(shortUrl)
            linkParser.parse(resolved)
        } catch (ex: Exception) {
            return LinkParseResult(success = false)
        } ?: return LinkParseResult(success = false)
        return LinkParseResult(success = true, place = place)
    }
}
