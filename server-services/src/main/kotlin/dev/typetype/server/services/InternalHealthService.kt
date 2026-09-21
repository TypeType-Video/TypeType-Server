package dev.typetype.server.services

import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.models.DeepHealthResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class InternalHealthService(
    private val cache: DragonflyService,
    private val downloaderGatewayService: DownloaderGatewayService,
    private val tokenServiceUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun check(): DeepHealthResponse {
        val checks = linkedMapOf(
            "postgres" to check(DatabaseFactory::healthCheck),
            "dragonfly" to check { cache.ping() },
            "downloader" to check(downloaderGatewayService::healthCheck),
            "token" to check(::tokenHealthCheck),
        )
        val status = if (checks.values.all { it == "ok" }) "ok" else "degraded"
        return DeepHealthResponse(status = status, checks = checks)
    }

    private suspend fun check(block: suspend () -> Boolean): String =
        if (runCatching { block() }.getOrDefault(false)) "ok" else "error"

    private suspend fun tokenHealthCheck(): Boolean = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url("${tokenServiceUrl.trimEnd('/')}/health").get().build())
            .execute()
            .use { it.isSuccessful }
    }
}
