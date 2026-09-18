package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrInitializationData
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.coversPlaybackTime
import dev.typetype.server.services.findCachedPlaybackMediaAt
import dev.typetype.server.services.isAcceptableLiveFollowingSegment
import dev.typetype.server.services.failLivePlaybackDiscontinuity
import dev.typetype.server.services.livePlaybackSnapshot
import dev.typetype.server.services.playbackContinuationSequence
import dev.typetype.server.services.playbackSegmentStartMs
import dev.typetype.server.services.resolvePlaybackStartMs
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat

private const val MAX_SEGMENTS_PER_TRACK = 12

internal class SabrPlaybackWindowBuilder(private val sabrSessionStore: SabrSessionStore) {
    suspend fun build(
        holder: SabrSessionHolder,
        request: SabrPlaybackWindowRequest,
    ): SabrPlaybackWindowBuildResult {
        val startTimeMs = holder.resolvePlaybackStartMs(request.playerTimeMs)
        val effectiveRequest = if (startTimeMs == request.playerTimeMs) request else request.copy(playerTimeMs = startTimeMs)
        val live = holder.livePlaybackSnapshot()
        SabrInitializationData.ingestRemembered(holder.audioFormat, holder)
        if (!effectiveRequest.audioOnly) SabrInitializationData.ingestRemembered(holder.videoFormat, holder)
        if (effectiveRequest.audioOnly) return buildAudioOnly(holder, effectiveRequest, live?.toResponse())
        val video = buildTrack(holder, holder.videoFormat, effectiveRequest, effectiveRequest.playerTimeMs, live?.active == true)
        val decodeStartMs = video.track.segments.firstOrNull()?.startMs ?: effectiveRequest.playerTimeMs
        val audioStartMs = if (live?.active == true) minOf(effectiveRequest.playerTimeMs, decodeStartMs) else effectiveRequest.playerTimeMs
        val audio = buildTrack(
            holder,
            holder.audioFormat,
            effectiveRequest,
            audioStartMs,
            live?.active == true,
        )
        val playbackStartMs = resolvedPlaybackStartMs(
            effectiveRequest.playerTimeMs,
            live?.active == true,
            video.track,
            audio.track,
        )
        val blocked = blockedTrack(audio, video)
        val readyAheadMs = readyAheadMs(effectiveRequest, live?.active == true)
        val requestedReadyEndMs = playbackStartMs + readyAheadMs
        return SabrPlaybackWindowBuildResult(
            response = SabrPlaybackWindowReadyResponse(
                sessionId = holder.sessionToken,
                generation = holder.activeGeneration(),
                ready = true,
                retryAfterMs = null,
                durationMs = holder.durationMs(),
                endOfStream = live?.active != true && audio.atEnd && video.atEnd,
                audio = audio.track,
                video = video.track,
                startTimeMs = playbackStartMs,
                live = live?.toResponse(),
            ),
            blockedBy = blocked?.blockedBy,
            blockedRequests = listOfNotNull(video.blockedRequest, audio.blockedRequest),
            isReady = audio.covers(holder.readyEndMs(holder.audioFormat, requestedReadyEndMs)) &&
                video.covers(holder.readyEndMs(holder.videoFormat, requestedReadyEndMs)),
        )
    }
    private suspend fun buildAudioOnly(
        holder: SabrSessionHolder,
        request: SabrPlaybackWindowRequest,
        live: SabrLivePlaybackResponse?,
    ): SabrPlaybackWindowBuildResult {
        val audio = buildTrack(holder, holder.audioFormat, request, request.playerTimeMs, live?.active == true)
        val playbackStartMs = resolvedPlaybackStartMs(request.playerTimeMs, live?.active == true, audio.track)
        val readyEndMs = playbackStartMs + readyAheadMs(request, live?.active == true)
        return SabrPlaybackWindowBuildResult(
            response = SabrPlaybackWindowReadyResponse(
                sessionId = holder.sessionToken,
                generation = holder.activeGeneration(),
                ready = true,
                retryAfterMs = null,
                durationMs = holder.durationMs(),
                endOfStream = live?.active != true && audio.atEnd,
                audio = audio.track,
                startTimeMs = playbackStartMs,
                live = live,
            ),
            blockedBy = audio.blockedBy,
            blockedRequests = listOfNotNull(audio.blockedRequest),
            isReady = audio.covers(holder.readyEndMs(holder.audioFormat, readyEndMs)),
        )
    }
    private fun blockedTrack(audio: TrackBuildResult, video: TrackBuildResult): TrackBuildResult? =
        sequenceOf(video, audio)
            .filter { it.blockedRequest != null }
            .minByOrNull { it.coveredEndMs }
    private suspend fun buildTrack(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        request: SabrPlaybackWindowRequest,
        requestedStartMs: Long,
        activeLive: Boolean,
    ): TrackBuildResult {
        val continuesServedTrack = !activeLive || holder.lastServedSequence(format) != null
        val targetMs = if (continuesServedTrack) request.bufferedEndFor(format, requestedStartMs) else requestedStartMs.coerceAtLeast(0L)
        val goalStartMs = if (activeLive) targetMs else request.playerTimeMs.coerceAtLeast(0L)
        var goalEndMs = maxOf(goalStartMs, request.playerTimeMs.coerceAtLeast(0L)) +
            request.bufferGoalMs.coerceAtLeast(1L)
        val segments = mutableListOf<SabrPlaybackWindowSegment>()
        var blockedBy: String? = null
        var blockedRequest: SabrSegmentRequest? = null
        var seq = holder.playbackContinuationSequence(format, targetMs, activeLive)
        var coveredEndMs = targetMs
        val endSequence = if (activeLive) 0 else holder.session.streamState.getEndSegment(format).toInt()
        var atEnd = false
        while (segments.size < MAX_SEGMENTS_PER_TRACK) {
            if (endSequence > 0 && seq > endSequence) {
                atEnd = true
                break
            }
            val mediaRequest = SabrSegmentRequest.media(format, seq)
            var segment = sabrSessionStore.cachedSegment(holder, mediaRequest)
            val expectedStartMs = if (segments.isEmpty()) targetMs else coveredEndMs
            if (segment == null || segments.isEmpty() && !segment.coversPlaybackTime(holder, format, targetMs)) {
                val authoritative = sabrSessionStore.findCachedPlaybackMediaAt(
                    holder, format, expectedStartMs, seq, activeLive && segments.isEmpty(),
                )
                if (authoritative != null &&
                    (!activeLive || authoritative.isAcceptableLiveFollowingSegment(seq, targetMs, continuesServedTrack))
                ) {
                    seq = authoritative.sequence
                    holder.session.streamState.jumpBufferedTo(format, seq)
                    segment = authoritative
                }
            }
            if (segment == null) {
                val progressive = if (activeLive) null else segments.appendProgressiveWindowSegment(
                    holder, format, seq, expectedStartMs,
                )
                if (progressive != null) {
                    seq = progressive.nextSequence
                    coveredEndMs = progressive.coveredEndMs
                    if (coveredEndMs >= goalEndMs) break
                    if (progressive.hasReadableMedia) continue
                    blockedRequest = SabrSegmentRequest.media(format, seq)
                    blockedBy = "${format.trackName()}:${format.itag}:$seq pending"
                    break
                }
                blockedBy = "${format.trackName()}:${format.itag}:$seq pending"
                blockedRequest = mediaRequest
                break
            }
            if (segments.isEmpty() && holder.failLivePlaybackDiscontinuity(
                    format, targetMs, segment, continuesServedTrack && request.bufferedRanges.any { it.itag == format.itag },
                )
            ) {
                blockedBy = "${format.trackName()}:${format.itag}:$seq discontinuity"
                break
            }
            if (!activeLive && segments.isEmpty() && segment.startMs > targetMs && seq > 1) {
                seq = holder.previousPlaybackSequence(format, seq, segment, targetMs)
                continue
            }
            val windowSegment = segment.toWindowSegment(
                holder,
                format,
                sabrSessionStore.resolvePlaybackDurationMs(holder, format, segment),
            )
            segments += windowSegment
            if (activeLive && segments.size == 1) {
                goalEndMs = maxOf(goalEndMs, windowSegment.startMs + request.bufferGoalMs.coerceAtLeast(1L))
            }
            coveredEndMs = windowSegment.startMs + windowSegment.durationMs
            if (endSequence > 0 && seq >= endSequence) {
                atEnd = true
                break
            }
            if (coveredEndMs >= goalEndMs) break
            seq++
        }
        if (blockedBy == null && coveredEndMs < goalEndMs && !atEnd) {
            blockedBy = "${format.trackName()}:${format.itag}:$seq window capped"
        }
        return TrackBuildResult(
            track = SabrPlaybackWindowTrack(
                mime = format.mimeType.orEmpty(),
                initUrl = "${SabrPlaybackPaths.mediaBasePath(holder.sessionToken)}/${format.itag}/init" +
                    "?generation=${holder.activeGeneration()}",
                segments = segments,
            ),
            blockedBy = blockedBy,
            blockedRequest = blockedRequest,
            coveredEndMs = coveredEndMs,
            atEnd = atEnd,
        )
    }
    private fun CachedSabrSegment.toWindowSegment(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        resolvedDurationMs: Long,
    ): SabrPlaybackWindowSegment {
        val startMs = startMs.takeIf { it >= 0L }
            ?: holder.playbackSegmentStartMs(format, sequence)
        return SabrPlaybackWindowSegment(
            url = "${SabrPlaybackPaths.mediaBasePath(holder.sessionToken)}/${format.itag}/segment/$sequence?generation=${holder.activeGeneration()}",
            startMs = startMs,
            durationMs = resolvedDurationMs,
        )
    }
    private data class TrackBuildResult(
        val track: SabrPlaybackWindowTrack,
        val blockedBy: String?,
        val blockedRequest: SabrSegmentRequest?,
        val coveredEndMs: Long,
        val atEnd: Boolean,
    ) {
        fun covers(requiredEndMs: Long): Boolean = (track.segments.isNotEmpty() || atEnd) && coveredEndMs >= requiredEndMs
    }
}
