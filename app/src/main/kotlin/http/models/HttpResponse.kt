package http.models

import http.HttpServer

class HttpResponse {
    private val headers = mutableMapOf<String, String>()
    private var body: String = ""
    private var status: Int = 200
    private val phrase: String
        get() = when (status) {
            200 -> "OK"
            201 -> "Created"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "Unknown Status"
        }

    fun setHeader(key: String, value: String): HttpResponse {
        headers[key] = value
        return this
    }

    fun setBody(body: String): HttpResponse {
        this.body = body
        return this
    }

    fun setStatus(status: Int): HttpResponse {
        this.status = status
        return this
    }

    fun build(): String {
        val length: Int = body.length
        val stringBuilder = StringBuilder()
        stringBuilder.append("HTTP/1.1 $status $phrase").append(HttpServer.Parser.LINE_BREAK)
        headers["Content-Length"] = length.toString()
        for ((key, value) in headers) {
            stringBuilder.append("$key: $value").append(HttpServer.Parser.LINE_BREAK)
        }
        stringBuilder.append(HttpServer.Parser.LINE_BREAK) // Blank line between headers and body
        stringBuilder.append(body)

        return stringBuilder.toString()
    }
}
