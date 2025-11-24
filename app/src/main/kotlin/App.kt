import java.io.BufferedWriter
import java.io.ObjectOutputStream
import java.io.OutputStreamWriter
import java.net.ServerSocket;

const val LINE_BREAK = "\r\n" // Carriage return + line feed (CRLF)
const val PORT = 4221

class ResponseBuilder {
    private val headers = mutableMapOf<String, String>()
    private var body: String = ""

    fun setHeader(key: String, value: String): ResponseBuilder {
        headers[key] = value
        return this
    }

    fun setBody(body: String): ResponseBuilder {
        this.body = body
        return this
    }

    fun build(): String {
        val length: Int = body.length
        val stringBuilder = StringBuilder()
        stringBuilder.append("HTTP/1.1 200 OK").append(LINE_BREAK)
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
    val serverSocket = ServerSocket(PORT)

    //#region Socket Options
    // Since the tester restarts your program quite often, setting SO_REUSEADDR
    // ensures that we don't run into 'Address already in use' errors
    serverSocket.reuseAddress = true
    //#endregion

    println("listening on port $PORT")

    while (true) {
        val clientSocket = serverSocket.accept() // Wait for connection from client.

        // val ois = ObjectInputStream(clientSocket.getInputStream())
        val out = BufferedWriter(
            OutputStreamWriter(clientSocket.getOutputStream())
        )

        out.write(ResponseBuilder().build())
        out.flush() // Ensure all data is sent out

        println("accepted new connection")
        clientSocket.close()
    }

    serverSocket.close()
}
