package http.compression

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

enum class CompressionAlgorithm {
    GZIP
}

object HttpCompression {
    fun compress(content: String, algorithm: CompressionAlgorithm): ByteArray {
        return when (algorithm) {
            CompressionAlgorithm.GZIP -> gzipCompress(content)
        }
    }

    private fun gzipCompress(content: String): ByteArray {
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream)
            .bufferedWriter(StandardCharsets.UTF_8)
            .use { it.write(content) }

        return byteStream.toByteArray() // Compressed Bytes
    }
}