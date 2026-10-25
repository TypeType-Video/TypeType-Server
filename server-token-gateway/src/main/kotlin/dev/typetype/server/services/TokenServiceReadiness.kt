package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class TokenServiceReadiness(
    private val tokenServiceUrl: String,
    client: OkHttpClient = OkHttpClient(),
) {
    private val client: OkHttpClient = client.newBuilder()
        .connectTimeout(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        when (val ready = status("/ready")) {
            NOT_FOUND -> status("/health") in SUCCESS
            else -> ready in SUCCESS
        }
    }

    private fun status(path: String): Int =
        client.newCall(Request.Builder().url("${tokenServiceUrl.trimEnd('/')}$path").get().build())
            .execute()
            .use { it.code }

    private companion object {
        const val CHECK_TIMEOUT_SECONDS = 5L
        const val NOT_FOUND = 404
        val SUCCESS = 200..299
    }
}
