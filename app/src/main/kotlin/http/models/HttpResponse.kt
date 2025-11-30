package http.models

sealed class HttpBody {
    data class StringBody(val content: String) : HttpBody()
    data class ByteArrayBody(val content: ByteArray) : HttpBody()
}

class HttpResponse {
    private val headers = mutableMapOf<String, String>()
    var body: HttpBody = HttpBody.StringBody("")
    var status: Int = 200
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

    fun setBody(body: ByteArray): HttpResponse {
        this.body = HttpBody.ByteArrayBody(body)
        return this
    }
    fun setBody(body: String): HttpResponse {
        this.body = HttpBody.StringBody(body)
        return this
    }

    fun setStatus(status: Int): HttpResponse {
        this.status = status
        return this
    }

    fun build(): String {
        val length: Int = when (body) {
            is HttpBody.StringBody -> (body as HttpBody.StringBody).content.length
            is HttpBody.ByteArrayBody -> (body as HttpBody.ByteArrayBody).content.size
        }

        val stringBuilder = StringBuilder()
        stringBuilder.append("HTTP/1.1 $status $phrase").append(HttpServer.LINE_BREAK)
        headers["Content-Length"] = length.toString()
        for ((key, value) in headers) {
            stringBuilder.append("$key: $value").append(HttpServer.LINE_BREAK)
        }
        stringBuilder.append(HttpServer.LINE_BREAK) // Blank line between headers and body

        return stringBuilder.toString()
    }
}
