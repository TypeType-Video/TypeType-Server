package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest

internal class SabrPlaybackMediaFetcher(private val sessionStore: SabrSessionStore) {
    suspend fun fetch(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        timeoutMs: Long,
        generation: Long,
    ): SabrPlaybackSegmentResult = if (holder.livePlaybackSnapshot()?.active == true) {
        fetchLive(holder, request, timeoutMs, generation)
    } else {
        fetchProgressive(holder, request, timeoutMs, generation)
    }

    private suspend fun fetchProgressive(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        timeoutMs: Long,
        generation: Long,
    ): SabrPlaybackSegmentResult {
        holder.session.getReadableSegment(request)?.let {
            return stream(holder, request, it, generation)
        }
        sessionStore.requestSegmentDemand(holder, request, generation)
        val segment = runInterruptible(Dispatchers.IO) {
            holder.session.awaitReadableSegment(request, timeoutMs)
        }
        return if (segment == null) {
            SabrPlaybackSegmentResult.Retry(holder, REPOSITIONING)
        } else {
            stream(holder, request, segment, generation)
        }
    }

    private suspend fun fetchLive(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        timeoutMs: Long,
        generation: Long,
    ): SabrPlaybackSegmentResult {
        sessionStore.cachedSegment(holder, request)?.let {
            holder.markServed(it, generation)
            return SabrPlaybackSegmentResult.Ready(it.mimeType, it.bytes)
        }
        sessionStore.requestSegmentDemand(holder, request, generation)
        val segment = awaitCachedLive(holder, request, timeoutMs)
        return if (segment == null) SabrPlaybackSegmentResult.Retry(holder, REPOSITIONING) else {
            holder.clearSegmentDemand(request)
            holder.markServed(segment, generation)
            SabrPlaybackSegmentResult.Ready(segment.mimeType, segment.bytes)
        }
    }

    private suspend fun awaitCachedLive(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        timeoutMs: Long,
    ): CachedSabrSegment? = withTimeoutOrNull(timeoutMs) {
        var segment = sessionStore.cachedSegment(holder, request)
        while (segment == null && holder.terminalFailure() == null && holder.networkFailure() == null) {
            delay(SEGMENT_WAIT_MS)
            segment = sessionStore.cachedSegment(holder, request)
            if (segment == null) segment = followingLiveSegment(holder, request)
        }
        segment
    }

    private suspend fun followingLiveSegment(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
    ): CachedSabrSegment? {
        val targetMs = holder.playbackSegmentStartMs(request.format, request.sequenceNumber)
        return sessionStore.findCachedPlaybackMediaAt(
            holder = holder,
            format = request.format,
            targetMs = targetMs,
            predictedSequence = request.sequenceNumber,
            allowFollowing = true,
        )?.takeUnless {
            holder.failLivePlaybackDiscontinuity(
                request.format,
                targetMs,
                it,
                holder.lastServedSequence(request.format) != null,
            )
        }
    }

    private fun stream(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        segment: SabrMediaSegment,
        generation: Long,
    ): SabrPlaybackSegmentResult.Stream {
        holder.clearSegmentDemand(request)
        return SabrPlaybackSegmentResult.Stream(request.format.mimeType.orEmpty(), segment, holder, generation)
    }

    private companion object {
        const val REPOSITIONING = "repositioning"
        const val SEGMENT_WAIT_MS = 250L
    }
}
