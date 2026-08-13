package com.juiceplan.source

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class ParseLinkRequest(val url: String)

@RestController
class SourceLinkController(private val linkService: GoogleMapsSourceLinkService) {

    @PostMapping("/api/sources/parse-link")
    fun parseLink(@RequestBody request: ParseLinkRequest): LinkParseResult {
        return linkService.parseLink(request.url)
    }
}
