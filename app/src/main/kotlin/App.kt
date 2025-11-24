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
    server.addHandler(HttpMethod.GET, "/file/*") { request, response ->
        if (rootDirPath == null) {
            response
                .setStatus(400)
                .setHeader("Content-Type", "text/plain")
                .setBody("File serving directory not specified.")
            return@addHandler response
        }

        val endpoint = "/file"
        val path = request.getPath()
        val filePathStartIndex = path?.lastIndexOf(endpoint)
        val fileName = if (filePathStartIndex != null && filePathStartIndex + endpoint.length < path.length) {
            path.substring(filePathStartIndex + endpoint.length).trimStart('/')
        } else {
            ""
        }

        if (fileName.isNotEmpty()) {
            val fileContents = File("$rootDirPath/$fileName").readText()
            response
                .setHeader("Content-Type", "text/plain")
                .setBody(fileContents)
        } else {
            response
                .setStatus(400)
                .setHeader("Content-Type", "text/plain")
                .setBody("File name not specified.")
        }

        response
    }

    server.start()
}


