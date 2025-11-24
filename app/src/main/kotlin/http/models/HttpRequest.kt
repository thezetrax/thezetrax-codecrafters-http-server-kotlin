package http.models

import http.types.HttpMethod

class HttpRequest(private val requestLine: HttpRequestLine, val headers: Map<String, String>, val body: String) {
    /**
     * Converts the method string to HttpMethod enum.
     */
    fun getMethod(): HttpMethod? {
        return this.requestLine.method
    }

    /**
     * Returns the requested path.
     */
    fun getPath(): String? {
        return this.requestLine.path
    }
}
