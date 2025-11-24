package http.models

import http.types.HttpMethod

data class HttpRequestLine(
    val method: HttpMethod,
    val path: String,
    val httpVersion: String
) {
    companion object {
        fun fromString(method: String, path: String, httpVersion: String): HttpRequestLine {
            val methodParsed = when (method) {
                "GET" -> HttpMethod.GET
                "POST" -> HttpMethod.POST
                "PUT" -> HttpMethod.PUT
                "DELETE" -> HttpMethod.DELETE
                "PATCH" -> HttpMethod.PATCH
                "OPTIONS" -> HttpMethod.OPTIONS
                "HEAD" -> HttpMethod.HEAD
                else -> throw IllegalArgumentException("Invalid HTTP method")
            }
            return HttpRequestLine(methodParsed, path, httpVersion)
        }
    }
}

