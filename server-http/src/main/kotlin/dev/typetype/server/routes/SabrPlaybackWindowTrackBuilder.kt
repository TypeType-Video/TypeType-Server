package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.coversPlaybackTime
import dev.typetype.server.services.failLivePlaybackDiscontinuity
import dev.typetype.server.services.findCachedPlaybackMediaAt
import dev.typetype.server.services.isAcceptableLiveFollowingSegment
import dev.typetype.server.services.playbackContinuationSequence
import dev.typetype.server.services.playbackSegmentStartMs
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat

private const val MAX_SEGMENTS_PER_TRACK = 12

internal class SabrPlaybackWindowTrackBuilder(private val sabrSessionStore: SabrSessionStore) {
    suspend fun buildTrack(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        request: SabrPlaybackWindowRequest,
        requestedStartMs: Long,
        activeLive: Boolean,
        requiredEndMs: Long? = null,
    ): TrackBuildResult {
        val continuesServedTrack = !activeLive || holder.lastServedSequence(format) != null
        val targetMs = if (continuesServedTrack) request.bufferedEndFor(format, requestedStartMs) else requestedStartMs.coerceAtLeast(0L)
        val goalStartMs = if (activeLive) targetMs else request.playerTimeMs.coerceAtLeast(0L)
        val liveBufferGoalMs = liveWindowBufferGoalMs(request.bufferGoalMs, activeLive)
        var goalEndMs = maxOf(goalStartMs, request.playerTimeMs.coerceAtLeast(0L)) + liveBufferGoalMs
        if (activeLive && requiredEndMs != null) goalEndMs = maxOf(goalEndMs, requiredEndMs)
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
                    if (!progressive.hasReadableMedia) {
                        blockedRequest = mediaRequest
                        blockedBy = "${format.trackName()}:${format.itag}:$seq pending"
                        break
                    }
                    seq = progressive.nextSequence
                    coveredEndMs = progressive.coveredEndMs
                    if (coveredEndMs >= goalEndMs) break
                    continue
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
                goalEndMs = maxOf(goalEndMs, windowSegment.startMs + liveBufferGoalMs)
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
}

internal data class TrackBuildResult(
    val track: SabrPlaybackWindowTrack,
    val blockedBy: String?,
    val blockedRequest: SabrSegmentRequest?,
    val coveredEndMs: Long,
    val atEnd: Boolean,
) {
    fun covers(requiredEndMs: Long): Boolean =
        blockedBy == null && (track.segments.isNotEmpty() || atEnd) && coveredEndMs >= requiredEndMs
}
