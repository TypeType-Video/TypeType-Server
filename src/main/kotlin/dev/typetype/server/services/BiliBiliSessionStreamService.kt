package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class BiliBiliSessionStreamService(
    private val streamService: StreamService,
    private val sessionService: BiliBiliSessionService,
) {
    suspend fun getStreamInfo(userId: String, url: String): ExtractionResult<StreamResponse>? {
        if (!isBiliBiliUrl(url)) return null
        val cookies = sessionService.connectedCookies(userId) ?: return null
        return BiliBiliSessionScope.withCredentials(cookies) {
            val result = streamService.getStreamInfo(url)
            if (result is ExtractionResult.Success) sessionService.markUsed(userId)
            if (result is ExtractionResult.Failure && requiresReconnect(result)) sessionService.markNeedsReconnect(userId)
            result
        }
    }

    private fun isBiliBiliUrl(url: String): Boolean =
        url.contains("bilibili.com") || url.contains("b23.tv")

    private fun requiresReconnect(result: ExtractionResult.Failure): Boolean =
        result.message.contains("412") ||
            result.message.contains("-352") ||
            result.message.contains("risk", ignoreCase = true)
}
