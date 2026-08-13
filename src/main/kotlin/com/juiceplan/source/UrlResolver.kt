package com.juiceplan.source

interface UrlResolver {
    fun resolve(shortUrl: String): String
}
