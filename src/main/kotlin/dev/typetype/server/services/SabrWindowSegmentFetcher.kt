package dev.typetype.server.services

import org.schabi.newpipe.extractor.localization.Localization
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import org.slf4j.LoggerFactory

internal object SabrWindowSegmentFetcher {
    suspend fun fetch(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        localization: Localization,
        segmentCache: SabrSegmentCache?,
    ): SabrMediaSegment? {
        val result = runCatchingNonCancellation {
            holder.withPlayerContext { pumpOnceStreamingUntilCached(localization, request) }
            holder.session.getCachedSegment(request)
        }
        result.onFailure { error ->
            SabrPlaybackDiagnostics.record(holder, request, error.message)
            logger.warn(
                "sabr_window event=fetch_failed videoId={} itag={} seq={} errorType={} error={}",
                holder.key.videoId,
                request.format.itag,
                request.sequenceNumber,
                error.javaClass.simpleName,
                error.message,
                error,
            )
        }
        val segment = result.getOrNull()
        if (segment != null) segmentCache?.put(holder, segment)
        return segment?.takeIf { it.matches(request) }
            ?.also { SabrPlaybackDiagnostics.clear(holder, it) }
            ?: holder.session.getCachedSegment(request)
    }

    private fun SabrMediaSegment.matches(request: SabrSegmentRequest): Boolean {
        if (header.isInitSegment || request.isInitializationSegment) return false
        return header.itag == request.format.itag && header.sequenceNumber == request.sequenceNumber
    }

    private val logger = LoggerFactory.getLogger(SabrWindowSegmentFetcher::class.java)
}
