package com.juiceplan.source

import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class HttpUrlResolver : UrlResolver {

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    override fun resolve(shortUrl: String): String {
        val request = HttpRequest.newBuilder(URI.create(shortUrl))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        return response.uri().toString()
    }
}
