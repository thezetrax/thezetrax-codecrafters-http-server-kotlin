import java.io.PrintWriter
import java.net.ServerSocket;

const val LINE_BREAK = "\r\n" // Carriage return + line feed (CRLF)

fun main() {
    val serverSocket = ServerSocket(4221)

    //#region Socket Options
    // Since the tester restarts your program quite often, setting SO_REUSEADDR
    // ensures that we don't run into 'Address already in use' errors
    serverSocket.reuseAddress = true
    //#endregion

    val clientSocket = serverSocket.accept() // Wait for connection from client.
    println("accepted new connection")
    val writer = PrintWriter(clientSocket.getOutputStream(), true)

    val response = """
        HTTP/1.1 200 OK$LINE_BREAK
    """.trimIndent()

    writer.println(response)
}
