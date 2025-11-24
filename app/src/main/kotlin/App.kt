import http.HttpServer
import http.types.HttpMethod

const val PORT = 4221

fun main() {
    val server = HttpServer(PORT)

    // Setup handlers
    server.addHandler(HttpMethod.GET, "/", { _, response -> response })
    server.addHandler(HttpMethod.GET, "/echo/*", { request, response ->
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
    })

    server.start()
}


