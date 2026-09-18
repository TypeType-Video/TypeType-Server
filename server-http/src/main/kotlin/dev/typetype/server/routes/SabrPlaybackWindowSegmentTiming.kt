package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.playbackSegmentDurationMs
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat

internal suspend fun SabrSessionStore.resolvePlaybackDurationMs(
    holder: SabrSessionHolder,
    format: YoutubeSabrFormat,
    segment: CachedSabrSegment,
): Long {
    segment.durationMs.takeIf { it > 0L }?.let { return it }
    val startMs = segment.startMs.takeIf { it >= 0L }
        ?: return holder.playbackSegmentDurationMs(format, segment.sequence)
    val next = cachedSegment(holder, SabrSegmentRequest.media(format, segment.sequence + 1))
    val nextStartMs = next?.startMs?.takeIf { it > startMs }
    return nextStartMs?.minus(startMs)
        ?: holder.playbackSegmentDurationMs(format, segment.sequence)
}
