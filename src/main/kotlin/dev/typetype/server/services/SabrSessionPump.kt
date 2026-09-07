package dev.typetype.server.services

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.localization.Localization
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import java.time.Instant

internal class SabrSessionPump(
    private val segmentCache: SabrSegmentCache? = null,
    refreshPoToken: (SabrSessionHolder) -> SabrTokenBundle? = { null },
) {
    private val loop = SabrSessionPumpLoop(
        unauthorizedRecovery = SabrUnauthorizedResponseRecovery(refreshPoToken),
        onResolved = { holder, segment -> segmentCache?.put(holder, segment) },
    )

    suspend fun ensureWarmed(holder: SabrSessionHolder, maxPumps: Int) {
        val localization = Localization("en", "US")
        var pumps = 0
        var liveWarmupTarget: SabrLiveWarmupTarget? = null
        holder.setPlaybackState(SabrPlaybackState.PREPARING)
        while (pumps < maxPumps &&
            !isWarmEnough(holder, liveWarmupTarget) &&
            (!holder.session.isComplete || holder.expectsLive())
        ) {
            val currentLiveTarget = liveWarmupTarget
            holder.pumpMutex.withLock {
                holder.setPlaybackState(SabrPlaybackState.REQUESTING)
                if (holder.expectsLive()) {
                    val segments = runCatchingNonCancellation {
                        withLiveWarmupRequestShape(holder, currentLiveTarget) {
                            holder.withPlayerContext { pumpOnce(localization) }
                        }
                    }
                        .getOrDefault(emptyList())
                    segments.forEach { segment ->
                        segmentCache?.put(holder, segment)
                        holder.observeMediaSegment(segment)
                    }
                    if (currentLiveTarget != null) {
                        liveWarmupTarget = holder.advanceLiveWarmupTarget(currentLiveTarget, segments)
                    }
                    if (liveWarmupTarget == null) liveWarmupTarget = holder.liveWarmupTarget()
                } else {
                    runCatchingNonCancellation { holder.withPlayerContext { pumpOnceStreaming(localization) } }
                }
            }
            pumps++
        }
        holder.pumpMutex.withLock {
            SabrInitializationData.ingest(holder.audioFormat, holder)
            SabrInitializationData.ingest(holder.videoFormat, holder)
        }
        holder.setPlaybackState(SabrPlaybackState.IDLE)
    }

    private fun isWarmEnough(holder: SabrSessionHolder, liveTarget: SabrLiveWarmupTarget?): Boolean {
        val audioObserved = holder.observedMediaSegment(holder.audioFormat) != null
        val videoObserved = !holder.isVideoActive() || holder.observedMediaSegment(holder.videoFormat) != null
        if (holder.expectsLive()) {
            val target = liveTarget ?: return false
            val audioStartMs = holder.earliestObservedMediaStartMs(holder.audioFormat) ?: return false
            val videoStartMs = if (holder.isVideoActive()) {
                holder.earliestObservedMediaStartMs(holder.videoFormat) ?: return false
            } else {
                audioStartMs
            }
            return maxOf(audioStartMs, videoStartMs) <= target.timeMs + target.segmentDurationMs
        }
        return bothFormatsKnown(holder) ||
            holder.session.streamState.getMaxSegment(holder.audioFormat) > 0 &&
            holder.session.streamState.getMaxSegment(holder.videoFormat) > 0
    }

    suspend fun fetchSegment(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
    ): SabrMediaSegment? = fetchSegment(holder, request, markServed = true)

    private suspend fun fetchSegment(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        markServed: Boolean,
    ): SabrMediaSegment? {
        holder.lastRequestAt = Instant.now()
        holder.session.getCachedSegment(request)?.let { segment ->
            if (markServed) holder.markServed(segment)
            holder.clearSegmentDemand(request)
            segmentCache?.put(holder, segment)
            return segment
        }
        if (holder.session.isBeyondEnd(request) && !holder.isFutureLiveRequest(request)) return null
        val localization = Localization("en", "US")
        if (request.isInitializationSegment) {
            return fetchSabrInitializationSegment(holder, request, localization, segmentCache, markServed)
        }
        return fetchMediaSegment(holder, request, localization, markServed)
    }

    suspend fun fetchMediaAt(holder: SabrSessionHolder, playerTimeMs: Long): List<SabrMediaSegment>? =
        SabrSessionMediaFetcher.fetch(holder, playerTimeMs) { request ->
            fetchTargeted(holder, request, Localization("en", "US"), markServed = false)
        }?.also { segments ->
            segmentCache?.putAll(holder, segments)
            segments.forEach { holder.markPrepared(it) }
        }

    suspend fun pumpLoop(isAlive: () -> Boolean, holder: SabrSessionHolder, intervalMs: Long): Unit =
        loop.run(isAlive, holder, intervalMs)

    private suspend fun fetchMediaSegment(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        localization: Localization,
        markServed: Boolean,
    ): SabrMediaSegment? {
        if (shouldFillSequentially(holder, request)) {
            fetchSequentially(holder, request, localization, markServed)?.let { return it }
        }
        return fetchTargeted(holder, request, localization, markServed)
    }

    private suspend fun fetchSequentially(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        localization: Localization,
        markServed: Boolean,
    ): SabrMediaSegment? {
        var pumps = 0
        while (pumps < SEQUENTIAL_PUMPS) {
            val segment = holder.pumpMutex.withLock {
                holder.session.getCachedSegment(request)?.let { cached ->
                    if (markServed) holder.markServed(cached)
                    return@withLock cached
                }
                if (holder.session.isBeyondEnd(request) && !holder.isFutureLiveRequest(request)) return@withLock null
                val exactSeekPrepared = holder.consumeMatchingSeek(request)
                val edgeMs = holder.session.streamState.getMinBufferedEndMs()
                val startMs = holder.session.streamState.getSegmentStartMs(request.format, request.sequenceNumber)
                if (startMs < edgeMs - REWIND_GAP_MS) {
                    holder.setReaderPosition(request.format, startMs.coerceAtLeast(0L))
                    if (!exactSeekPrepared) holder.session.prepareForRewind(request)
                    runCatchingNonCancellation { holder.withPlayerContext { pumpOnceStreamingUntilCached(localization, request) } }
                    return@withLock holder.session.getCachedSegment(request)?.also {
                        if (markServed) holder.markServed(it)
                    }
                } else {
                    holder.session.streamState.setPlayerTimeMs(maxOf(edgeMs, startMs + PLAYER_TIME_OFFSET_MS))
                }
                val fetched = runCatchingNonCancellation { holder.withPlayerContext { fetchSegment(request, localization) } }.getOrNull()
                fetched?.also { if (markServed) holder.markServed(it) }
            }
            if (segment != null) {
                holder.clearSegmentDemand(request)
                segmentCache?.put(holder, segment)
            }
            if (segment != null || holder.session.isBeyondEnd(request) && !holder.isFutureLiveRequest(request)) return segment
            pumps++
            delay(FETCH_RETRY_DELAY_MS)
        }
        return null
    }

    private suspend fun fetchTargeted(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        localization: Localization,
        markServed: Boolean,
    ): SabrMediaSegment? {
        var attempt = 0
        while (attempt < FETCH_ATTEMPTS) {
            val segment = holder.pumpMutex.withLock {
                holder.session.getCachedSegment(request)?.let { cached ->
                    if (markServed) holder.markServed(cached)
                    return@withLock cached
                }
                if (holder.session.isBeyondEnd(request) && !holder.isFutureLiveRequest(request)) return@withLock null
                holder.consumeMatchingSeek(request)
                withTargetedRequestShape(holder, request) {
                    holder.withPlayerContext {
                        fetchTargetedSegment(holder, request, localization, targetPlayerTime(holder, request))
                    }
                        ?: SabrWindowSegmentFetcher.fetch(holder, request, localization, segmentCache)
                }
            }
            if (segment != null) {
                if (markServed) holder.markServed(segment)
                holder.clearSegmentDemand(request)
                segmentCache?.put(holder, segment)
            }
            if (segment != null || holder.session.isBeyondEnd(request) && !holder.isFutureLiveRequest(request)) return segment
            attempt++
            delay(FETCH_RETRY_DELAY_MS)
        }
        return null
    }

    private fun shouldFillSequentially(holder: SabrSessionHolder, request: SabrSegmentRequest): Boolean {
        if (targetPlayerTime(holder, request) != null) return false
        val startMs = holder.session.streamState.getSegmentStartMs(request.format, request.sequenceNumber)
        val edgeMs = holder.session.streamState.getMinBufferedEndMs()
        return startMs <= INITIAL_SEQUENTIAL_LIMIT_MS || startMs <= edgeMs + SEQUENTIAL_FILL_AHEAD_MS
    }

    private fun targetPlayerTime(holder: SabrSessionHolder, request: SabrSegmentRequest): Long? {
        val playerTimeMs = holder.playerTimeMs()
        val startMs = holder.session.streamState.getSegmentStartMs(request.format, request.sequenceNumber)
        val edgeMs = holder.session.streamState.getMinBufferedEndMs()
        if (startMs <= edgeMs + SEQUENTIAL_FILL_AHEAD_MS) return null
        if (playerTimeMs < startMs) return null
        val endMs = holder.session.streamState.getSegmentEndMs(request.format, request.sequenceNumber)
        return playerTimeMs.takeIf { it < endMs }
    }

}
