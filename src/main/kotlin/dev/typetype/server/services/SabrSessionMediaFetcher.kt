package dev.typetype.server.services

import kotlinx.coroutines.sync.withLock
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import org.slf4j.LoggerFactory
import java.time.Instant

internal object SabrSessionMediaFetcher {
    private val logger = LoggerFactory.getLogger(SabrSessionMediaFetcher::class.java)

    suspend fun fetch(
        holder: SabrSessionHolder,
        playerTimeMs: Long,
        fetchSegment: suspend (SabrSegmentRequest) -> SabrMediaSegment?,
    ): List<SabrMediaSegment>? {
        holder.lastRequestAt = Instant.now()
        val requests = holder.pumpMutex.withLock { holder.mediaRequestsAt(playerTimeMs) }
        if (requests.isEmpty()) return emptyList()
        return requests.map { request ->
            val result = runCatchingNonCancellation { fetchSegment(request) }
                .onFailure { error ->
                    logger.warn(
                        "sabr_media_fetch_failed videoId={} playerTimeMs={} itag={} sequence={} error={}",
                        holder.key.videoId,
                        playerTimeMs,
                        request.format.itag,
                        request.sequenceNumber,
                        error.message,
                        error,
                    )
                }
            result.getOrNull() ?: return null
        }
            .filter { holder.shouldSend(it) }
            .takeIf { it.isNotEmpty() }
    }
}
