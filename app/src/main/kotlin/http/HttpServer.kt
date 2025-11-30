import http.models.HttpBody
import http.models.HttpRequest
import http.models.HttpRequestLine
import http.models.HttpResponse
import http.types.HttpHandler
import http.types.HttpMethod
import http.types.Middleware
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import kotlinx.coroutines.*

class HttpServer(private val port: Int) {
    private val serverSocket = ServerSocket(port)
    private var handlerMap = mutableMapOf<Pair<HttpMethod, String>, HttpHandler>()
    private var preMiddlewareList = mutableListOf<Middleware>()
    private var postMiddlewareList = mutableListOf<Middleware>()

    companion object Parser {
        const val LINE_BREAK = "\r\n"

        /**
         * Parses an HTTP request line into its components.
         *
         * @throws IllegalArgumentException if the request line is invalid
         */
        fun parseRequestLine(requestLine: String): HttpRequestLine {
            val requestLineParts = requestLine.split(" ")
            if (requestLineParts.size >= 3) {
                return HttpRequestLine.fromString(
                    method = requestLineParts[0], path = requestLineParts[1], httpVersion = requestLineParts[2]
                )
            } else throw IllegalArgumentException("Invalid HTTP request line")
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
    fun start() = runBlocking {
        //#region Socket Options
        // Since the tester restarts your program quite often, setting SO_REUSEADDR
        // ensures that we don't run into 'Address already in use' errors
        serverSocket.reuseAddress = true
        //#endregion
        println("listening on port $port")

        // Main server loop to accept incoming connections
        while (true) {
            val socket = serverSocket.accept()
            launch(Dispatchers.IO) {
                socket.use { clientSocket ->
                    // Set up input and output streams
                    val readerIn = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                    // val writerOut = BufferedWriter(OutputStreamWriter(clientSocket.getOutputStream()))
                    val writerOut = clientSocket.getOutputStream()

                    val requestLine = Parser.parseRequestLine(readerIn.readLine())
                    var headerLines: MutableList<String> = mutableListOf()
                    var lineBuffer: String?
                    while (readerIn.readLine().also { lineBuffer = it } != null && lineBuffer!!.isNotEmpty()) {
                        headerLines.add(lineBuffer!!) // Read headers until an empty line is encountered
                    }

                    val contentLength =
                        headerLines.find { it.startsWith("Content-Length:") }?.split(":")?.get(1)?.trim()?.toIntOrNull()
                            ?: 0
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
                        headers = Parser.parseHeaders(headerLines),
                        body = bodyBuilder.toString()
                    )
                    val httpResponse = HttpResponse()

                    // build response, using handler callbacks
                    val response = when {
                        httpRequest.getMethod() != null && httpRequest.getPath() != null && handlerExists(
                            httpRequest.getMethod()!!,
                            httpRequest.getPath()!!
                        ) -> {
                            val handler = getHandler(httpRequest.getMethod()!!, httpRequest.getPath()!!)

                            if (handler != null) {
                                preMiddlewareList.fold(httpResponse) { preResponse, preMiddleware ->
                                    preMiddleware(httpRequest, preResponse)
                                }.let { res ->
                                    handler(httpRequest, res).let { res ->
                                        postMiddlewareList.fold(res) { postResponse, postMiddleware ->
                                            postMiddleware(httpRequest, postResponse)
                                        }
                                    }
                                }
                            } else {
                                internalErrorHandler(httpResponse)
                            }
                        }

                        else -> {
                            resourceNotFoundHandler(httpResponse)
                        }
                    }

                    val rawResponse = response.build()
                    writerOut.write(rawResponse.toByteArray())
                    when (response.body) {
                        is HttpBody.StringBody -> writerOut.write(
                            (response.body as HttpBody.StringBody).content.toByteArray())
                        is HttpBody.ByteArrayBody -> clientSocket.getOutputStream()
                            .write((response.body as HttpBody.ByteArrayBody).content)
                    }
                    writerOut.flush() // Ensure all data is sent out

                    println("handled new connection")
                }
            }
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
        return httpResponse.setStatus(500).setBody("500 Internal Server Error").setHeader("Content-Type", "text/plain")
    }

    /**
     * Adds a handler for the specified HTTP method and path.
     * @throws IllegalArgumentException if a handler already exists for the given method and path.
     */
    fun addHandler(method: HttpMethod, path: String, handler: HttpHandler) {
        if (this.handlerExists(method, path)) {
            throw IllegalArgumentException("Handler already exists for path: $path")
        }
        handlerMap[Pair(method, path)] = handler
    }

    fun addMiddleware(middleware: Middleware) {
        preMiddlewareList.add(middleware)
    }

    fun addPostMiddleware(middleware: Middleware) {
        postMiddlewareList.add(middleware)
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
        return handlerMap.entries.find { entry ->
                val (handlerMethod, handlerPath) = entry.key.first to this.normalizePath(entry.key.second)

                handlerMethod == method && (handlerPath == normalizedPath || handlerPath.endsWith("*") && normalizedPath.startsWith(
                    this.normalizePath(handlerPath.removeSuffix("*"))
                ))
            }?.value
    }

    // Normalizes the path by removing trailing slashes.
    private fun normalizePath(path: String): String = path.trimEnd('/')
}
