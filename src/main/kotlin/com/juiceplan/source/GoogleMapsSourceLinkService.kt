package com.juiceplan.source

import org.springframework.stereotype.Service

data class LinkParseResult(val success: Boolean, val place: ParsedPlace? = null)

@Service
class GoogleMapsSourceLinkService(
    private val urlResolver: UrlResolver,
    private val linkParser: GoogleMapsLinkParser
) {
    fun parseLink(shortUrl: String): LinkParseResult {
        val resolved = try {
            urlResolver.resolve(shortUrl)
        } catch (ex: Exception) {
            return LinkParseResult(success = false)
        }
        val place = linkParser.parse(resolved) ?: return LinkParseResult(success = false)
        return LinkParseResult(success = true, place = place)
    }
}
