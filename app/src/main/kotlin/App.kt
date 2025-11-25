import http.HttpServer
import http.types.HttpMethod
import java.io.File

const val PORT = 4221

fun main(args: Array<String>) {
    val server = HttpServer(PORT)

    val rootDirPath: String? = if (args.isNotEmpty() && args.size == 2 && args[0] == "--directory") {
        args[1]
    } else {
        null
    }

    server.addMiddleware { req, res ->
        val acceptEncodings: List<String> = req.getHeader("Accept-Encoding").let {
            it?.split(",")
        }
            ?: return@addMiddleware res

        val supportedEncodings = listOf("gzip", "deflate", "br", "identity")

        for (encoding in acceptEncodings) {
            val trimmedEncoding = encoding.trim()
            when {
                trimmedEncoding in supportedEncodings -> {
                    res.setHeader("Content-Encoding", trimmedEncoding)
                }
//                trimmedEncoding.startsWith("invalid-encoding") -> {
//                    return@addMiddleware res
//                }
            }
        }

        res
    }

    // Setup handlers
    server.addHandler(HttpMethod.GET, "/") { _, response -> response }
    server.addHandler(HttpMethod.GET, "/echo/*") { request, response ->
        val endpoint = "/echo"
        val path = request.getPath()
        val msgStartIndex = path?.lastIndexOf(endpoint)
        val msg = if (msgStartIndex != null && msgStartIndex + endpoint.length < path.length) {
            path.substring(msgStartIndex + endpoint.length).trimStart('/')
        } else {
            ""
        }

        response
            .setHeader("Content-Type", "text/plain")
            .setBody(msg)

        response
    }
    server.addHandler(HttpMethod.GET, "/user-agent") { request, response ->
        val userAgent = request.getHeader("User-Agent") ?: "Unknown"
        response
            .setHeader("Content-Type", "text/plain")
            .setBody(userAgent)

        response
    }
    server.addHandler(HttpMethod.GET, "/files/*") { request, response ->
        if (rootDirPath == null) {
            response
                .setStatus(400)
                .setHeader("Content-Type", "text/plain")
                .setBody("File serving directory not specified.")
            return@addHandler response
        }

        val endpoint = "/files"
        val path = request.getPath()
        val filePathStartIndex = path?.lastIndexOf(endpoint)
        val fileName = if (filePathStartIndex != null && filePathStartIndex + endpoint.length < path.length) {
            path.substring(filePathStartIndex + endpoint.length).trimStart('/')
        } else {
            ""
        }

        var file: File? = null
        when {
            fileName.isNotEmpty() && File("$rootDirPath/$fileName").also { file = it }.exists() -> {
                val fileContents = file!!.readText()
                response
                    .setHeader("Content-Type", "application/octet-stream")
                    .setBody(fileContents)
            }

            else -> {
                response
                    .setStatus(404)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("File does not exist")
            }
        }

        response
    }
    server.addHandler(HttpMethod.POST, "/files/*") { request, response ->
        val body = request.body
        if (rootDirPath == null) {
            response
                .setStatus(400)
                .setHeader("Content-Type", "text/plain")
                .setBody("File serving directory not specified.")
            return@addHandler response
        }
        val endpoint = "/files"
        val path = request.getPath()
        val filePathStartIndex = path?.lastIndexOf(endpoint)
        val fileName = if (filePathStartIndex != null && filePathStartIndex + endpoint.length < path.length) {
            path.substring(filePathStartIndex + endpoint.length).trimStart('/')
        } else {
            ""
        }
        var file: File? = null
        when {
            fileName.isNotEmpty() -> {
                file = File("$rootDirPath/$fileName")
                file.writeText(body ?: "")
                response
                    .setStatus(201)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("File created/overwritten successfully")
            }

            else -> {
                response
                    .setStatus(400)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("Invalid file name")
            }
        }

        response
    }

    server.start()
}


