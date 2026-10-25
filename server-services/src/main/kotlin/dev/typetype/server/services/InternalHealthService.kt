package dev.typetype.server.services

import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.models.DeepHealthResponse
import okhttp3.OkHttpClient

class InternalHealthService(
    private val cache: DragonflyService,
    private val downloaderGatewayService: DownloaderGatewayService,
    tokenServiceUrl: String,
    client: OkHttpClient = OkHttpClient(),
) {
    private val tokenReadiness = TokenServiceReadiness(tokenServiceUrl, client)

    suspend fun check(): DeepHealthResponse {
        val checks = linkedMapOf(
            "postgres" to check(DatabaseFactory::healthCheck),
            "dragonfly" to check { cache.ping() },
            "downloader" to check(downloaderGatewayService::healthCheck),
            "token" to check(tokenReadiness::isReady),
        )
        val status = if (checks.values.all { it == "ok" }) "ok" else "degraded"
        return DeepHealthResponse(status = status, checks = checks)
    }

    private suspend fun check(block: suspend () -> Boolean): String =
        if (runCatching { block() }.getOrDefault(false)) "ok" else "error"
}
