package dev.typetype.server.services

import dev.typetype.server.ai.AiImageInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.Proxy
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit

class FocusAiThumbnailFetcher(
    proxy: Proxy? = null,
    baseClient: OkHttpClient = OkHttpClient(),
) {
    private val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .apply {
            proxy?.let {
                proxy(it)
                connectionPool(ConnectionPool(PROXY_POOL_MAX_IDLE, PROXY_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS))
            }
        }
        .build()

    suspend fun fetch(thumbnailUrl: String): AiImageInput? {
        val trimmed = thumbnailUrl.trim()
        if (trimmed.isBlank()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if ((scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) return null

        val request = Request.Builder().url(trimmed).get().build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val contentType = response.header("Content-Type")
                        ?.substringBefore(';')
                        ?.trim()
                        ?.lowercase()
                    val mimeType = if (contentType != null && contentType.startsWith("image/")) {
                        contentType
                    } else {
                        "image/jpeg"
                    }
                    val bytes = readBoundedBytes(response.body.byteStream()) ?: return@withContext null
                    AiImageInput(
                        mimeType = mimeType,
                        base64Data = Base64.getEncoder().encodeToString(bytes),
                    )
                }
            } catch (_: IOException) {
                null
            }
        }
    }

    private fun readBoundedBytes(stream: java.io.InputStream): ByteArray? {
        val buffer = ByteArray(BUFFER_SIZE)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            total += read
            if (total > MAX_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray().takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val TIMEOUT_MS = 4_000L
        private const val MAX_BYTES = 2 * 1024 * 1024
        private const val BUFFER_SIZE = 8_192
        private const val PROXY_POOL_MAX_IDLE = 5
        private const val PROXY_KEEP_ALIVE_SECONDS = 25L
    }
}
