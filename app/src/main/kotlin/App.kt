import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket;

typealias HttpHandler = (HttpRequest, HttpResponse) -> HttpResponse

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

    // Setup handlers
    server.addHandler(HttpMethod.GET, "/", { _, response -> response })
    server.addHandler(HttpMethod.GET, "/echo/*", { request, response ->
        val endpoint = "/echo"
        val path = request.getPath()
        val msgStartIndex = path?.lastIndexOf(endpoint)
        val msg = if (msgStartIndex != null && msgStartIndex +  endpoint.length < path.length) {
            path.substring(msgStartIndex + endpoint.length).trimStart('/')
        } else {
            ""
        }

        response
            .setHeader("Content-Type", "text/plain")
            .setBody(msg)

        response
    })

    server.start()
}

class HttpServer(private val port: Int) {
    val serverSocket = ServerSocket(port)
    var handlerMap = mutableMapOf<Pair<HttpMethod, String>, HttpHandler>()

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

    /**
     * Starts the HTTP server to listen for incoming connections.
     */
    fun start() {
        //#region Socket Options
        // Since the tester restarts your program quite often, setting SO_REUSEADDR
        // ensures that we don't run into 'Address already in use' errors
        serverSocket.reuseAddress = true
        //#endregion
        println("listening on port $PORT")

        while (true) {
            val clientSocket = serverSocket.accept() // Wait for connection from client.

            // Set up input and output streams
            val readerIn = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val writerOut = BufferedWriter(OutputStreamWriter(clientSocket.getOutputStream()))

            val requestLine = Parsers.parseRequestLine(readerIn.readLine())
            var headerLines: MutableList<String> = mutableListOf()
            var lineBuffer: String?
            while (readerIn.readLine().also { lineBuffer = it } != null && lineBuffer!!.isNotEmpty()) {
                headerLines.add(lineBuffer!!) // Read headers until an empty line is encountered
            }

            val contentLength =
                headerLines.find { it.startsWith("Content-Length:") }?.split(":")?.get(1)?.trim()?.toIntOrNull() ?: 0
            var bodyBuilder = StringBuilder() // Read body based on Content-Length
            when {
                contentLength > 0 -> {
                    val bodyBuffer = CharArray(contentLength)
                    readerIn.read(bodyBuffer, 0, contentLength)
                    bodyBuilder.append(bodyBuffer)
                }

                else -> {
                    // No `body` to read
                    println("No body to read")
                }
            }

            val httpRequest = HttpRequest(
                requestLine = requestLine,
                headers = Parsers.parseHeaders(headerLines),
                body = bodyBuilder.toString()
            )
            val httpResponse = HttpResponse()

            // build response, using handler callbacks
            val response = when {
                httpRequest.getMethod() != null && httpRequest.getPath() != null &&
                        this.handlerExists(httpRequest.getMethod()!!, httpRequest.getPath()!!) -> {
                    val handler = this.getHandler(httpRequest.getMethod()!!, httpRequest.getPath()!!)

                    if (handler != null) {
                        handler(httpRequest, httpResponse)
                    } else {
                        internalErrorHandler(httpResponse)
                    }
                }

                else -> {
                    resourceNotFoundHandler(httpResponse)
                }
            }

            val rawResponse = response.build()
            writerOut.write(rawResponse)
            writerOut.flush() // Ensure all data is sent out

            println("accepted new connection")
            clientSocket.close()
        }

        serverSocket.close()
    }

    /**
     * Handles resource not found errors.
     */
    private fun resourceNotFoundHandler(httpResponse: HttpResponse): HttpResponse {
        return httpResponse.setStatus(404).setBody("404 Not Found").setHeader("Content-Type", "text/plain")
    }

    /**
     * Handles internal server errors.
     */
    private fun internalErrorHandler(httpResponse: HttpResponse): HttpResponse {
        return httpResponse.setStatus(500).setBody("500 Internal Server Error")
            .setHeader("Content-Type", "text/plain")
    }

    /**
     * Adds a handler for the specified HTTP method and path.
     * @throws IllegalArgumentException if a handler already exists for the given method and path.
     */
    fun addHandler(method: HttpMethod, path: String, handler: (HttpRequest, HttpResponse) -> HttpResponse) {
        if (this.handlerExists(method, path)) {
            throw IllegalArgumentException("Handler already exists for path: $path")
        }
        handlerMap[Pair(method, path)] = handler
    }

    /**
     * Checks if a handler exists for the given method and path.
     */
    private fun handlerExists(method: HttpMethod, path: String): Boolean {
        val normalizedPath = this.normalizePath(path)
        return handlerMap.any { (metadata, _) ->
            val (handlerMethod, handlerPath) = metadata.first to this.normalizePath(metadata.second)

            handlerMethod == method && (handlerPath == normalizedPath || handlerPath.endsWith("*") && normalizedPath.startsWith(
                this.normalizePath(handlerPath.removeSuffix("*"))
            ))
        }
    }

    /**
     * Retrieves the handler for the given method and path.
     */
    private fun getHandler(method: HttpMethod, path: String): HttpHandler? {
        val normalizedPath = this.normalizePath(path)
        return handlerMap.entries
            .find { entry ->
                val (handlerMethod, handlerPath) = entry.key.first to this.normalizePath(entry.key.second)

                handlerMethod == method && (handlerPath == normalizedPath || handlerPath.endsWith("*") && normalizedPath.startsWith(
                    this.normalizePath(handlerPath.removeSuffix("*"))
                ))
            }?.value
    }

    // Normalizes the path by removing trailing slashes.
    private fun normalizePath(path: String): String = path.trimEnd('/')
}
