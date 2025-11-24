import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket;

const val LINE_BREAK = "\r\n" // Carriage return + line feed (CRLF)
const val PORT = 4221

enum class HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    OPTIONS,
    HEAD
}

data class HttpRequestLine(
    val methodString: String,
    val path: String,
    val httpVersion: String
) {
    val method = when (methodString) {
        "GET" -> HttpMethod.GET
        "POST" -> HttpMethod.POST
        "PUT" -> HttpMethod.PUT
        "DELETE" -> HttpMethod.DELETE
        "PATCH" -> HttpMethod.PATCH
        "OPTIONS" -> HttpMethod.OPTIONS
        "HEAD" -> HttpMethod.HEAD
        else -> throw IllegalArgumentException("Invalid HTTP method")
    }
}

class HttpRequest(private val requestData: String) {
    private var headers = mutableMapOf<String, String>()
    private var body = StringBuilder()
    private var method: String? = null
    private var path: String? = null
    private var httpVersion: String? = null

    init {
        // Parse the request data here if needed
        val requestChunks = requestData.split(LINE_BREAK)
        val requestLine = requestChunks.get(0) // Request line

        val requestLineParts = requestLine.split(" ")
        if (requestLineParts.size >= 3) {
            method = requestLineParts[0]
            path = requestLineParts[1]
            httpVersion = requestLineParts[2]
        }

        // header/body separator index
        val separatorIndex = requestChunks.indexOfFirst { it.isEmpty() }
        val headerParts = if (separatorIndex > 0) requestChunks.slice(1 until separatorIndex) else emptyList()
        this.headers = headerParts.filter { it.contains(": ") }.associate { line ->
            val (key, value) = line.split(": ", limit = 2)
            key.trim() to value.trim()
        }.toMutableMap()

        if (separatorIndex >= 0 && separatorIndex < requestChunks.size - 1) {
            val bodyParts = requestChunks.slice(separatorIndex + 1 until requestChunks.size)
            bodyParts.forEach { line ->
                this.body.append(line).append(LINE_BREAK)
            }
        }
    }

    /**
     * Converts the method string to HttpMethod enum.
     */
    fun getMethod(): HttpMethod? {
        return when (method) {
            "GET" -> HttpMethod.GET
            "POST" -> HttpMethod.POST
            "PUT" -> HttpMethod.PUT
            "DELETE" -> HttpMethod.DELETE
            "PATCH" -> HttpMethod.PATCH
            "OPTIONS" -> HttpMethod.OPTIONS
            "HEAD" -> HttpMethod.HEAD
            else -> null
        }
    }

    /**
     * Returns the requested path.
     */
    fun getPath(): String? {
        return path
    }

    fun getBody(): String {
        return body.toString()
    }
}

class ResponseBuilder {
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

    fun setHeader(key: String, value: String): ResponseBuilder {
        headers[key] = value
        return this
    }

    fun setBody(body: String): ResponseBuilder {
        this.body = body
        return this
    }

    fun setStatus(status: Int): ResponseBuilder {
        this.status = status
        return this
    }

    fun build(): String {
        val length: Int = body.length
        val stringBuilder = StringBuilder()
        stringBuilder.append("HTTP/1.1 $status $phrase").append(LINE_BREAK)
        headers["Content-Length"] = length.toString()
        for ((key, value) in headers) {
            stringBuilder.append("$key: $value").append(LINE_BREAK)
        }
        stringBuilder.append(LINE_BREAK) // Blank line between headers and body
        stringBuilder.append(body)

        return stringBuilder.toString()
    }
}

fun main() {
    val server = HttpServer(PORT)

    server.addHandler(HttpMethod.GET, "/", { request, response ->
        println(request.getBody())

        response
    })

    server.start()
}

class HttpServer(private val port: Int) {
    val serverSocket = ServerSocket(port)
    var handlerMap = mutableMapOf<Pair<HttpMethod, String>, (HttpRequest, ResponseBuilder) -> ResponseBuilder>()

    object Parsers {
        /**
         * Parses an HTTP request line into its components.
         *
         * @throws IllegalArgumentException if the request line is invalid
         */
        fun parseRequestLine(requestLine: String): HttpRequestLine {
            val requestLineParts = requestLine.split(" ")
            if (requestLineParts.size >= 3) {
                return HttpRequestLine(
                    methodString = requestLineParts[0],
                    path = requestLineParts[1],
                    httpVersion = requestLineParts[2]
                )
            } else
                throw IllegalArgumentException("Invalid HTTP request line")
        }

        fun parseHeaders(headerLines: List<String>): Map<String, String> {
            val headers = mutableMapOf<String, String>()
            for (line in headerLines) {
                val parts = line.split(": ", limit = 2)
                if (parts.size == 2) {
                    headers[parts[0].trim()] = parts[1].trim()
                }
            }

            return headers
        }

        fun parseBody(bodyLines: List<String>): String {
            val bodyBuilder = StringBuilder()
            for (line in bodyLines) {
                bodyBuilder.append(line).append(LINE_BREAK)
            }
            return bodyBuilder.toString()
        }
    }

    fun start() {
        //#region Socket Options
        // Since the tester restarts your program quite often, setting SO_REUSEADDR
        // ensures that we don't run into 'Address already in use' errors
        serverSocket.reuseAddress = true
        //#endregion
        println("listening on port $PORT")

        while (true) {
            val clientSocket = serverSocket.accept() // Wait for connection from client.

            val readerIn = BufferedReader(
                InputStreamReader(clientSocket.getInputStream())
            )
            val writerOut = BufferedWriter(
                OutputStreamWriter(clientSocket.getOutputStream())
            )

            val requestLine = Parsers.parseRequestLine(readerIn.readLine())
            var headerLines: MutableList<String> = mutableListOf()
            var lineBuffer: String?
            while (readerIn.readLine().also { lineBuffer = it } != null && lineBuffer!!.isNotEmpty()) {
                headerLines.add(lineBuffer!!) // Read headers until an empty line is encountered
            }

            val contentLength =
                headerLines.find { it.startsWith("Content-Length:") }?.split(":")?.get(1)?.trim()?.toIntOrNull() ?: 0
            when {
                contentLength > 0 -> {
                    val bodyBuffer = CharArray(contentLength)
                    readerIn.read(bodyBuffer, 0, contentLength)

                    println("Body: " + String(bodyBuffer))
                }

                else -> {
                    // No body to read
                    println("No body to read")
                }
            }

            val rawRequest = readerIn.readLine()
            val httpRequest = HttpRequest(rawRequest)
            val responseBuilder = ResponseBuilder()

            // build response, using handler callbacks
            val response = when {
                httpRequest.getMethod() != null && httpRequest.getPath() != null &&
                        this.handlerExists(httpRequest.getMethod()!!, httpRequest.getPath()!!) -> {
                    val handler = this.getHandler(httpRequest.getMethod()!!, httpRequest.getPath()!!)

                    if (handler != null) {
                        handler(httpRequest, responseBuilder)
                    } else {
                        internalErrorHandler(responseBuilder)
                    }
                }

                else -> {
                    resourceNotFoundHandler(responseBuilder)
                }
            }

            writerOut.write(response.build())
            writerOut.flush() // Ensure all data is sent out

            println("accepted new connection")
            clientSocket.close()
        }

        serverSocket.close()
    }

    private fun resourceNotFoundHandler(responseBuilder: ResponseBuilder): ResponseBuilder {
        return responseBuilder.setStatus(404).setBody("404 Not Found").setHeader("Content-Type", "text/plain")
    }

    private fun internalErrorHandler(responseBuilder: ResponseBuilder): ResponseBuilder {
        return responseBuilder.setStatus(500).setBody("500 Internal Server Error")
            .setHeader("Content-Type", "text/plain")
    }

    fun addHandler(method: HttpMethod, path: String, handler: (HttpRequest, ResponseBuilder) -> ResponseBuilder) {
        handlerMap[Pair(method, path)] = handler
    }

    private fun handlerExists(method: HttpMethod, path: String): Boolean {
        return handlerMap.containsKey(Pair(method, path))
    }

    private fun getHandler(method: HttpMethod, path: String): ((HttpRequest, ResponseBuilder) -> ResponseBuilder)? {
        return handlerMap[Pair(method, path)]
    }
}
