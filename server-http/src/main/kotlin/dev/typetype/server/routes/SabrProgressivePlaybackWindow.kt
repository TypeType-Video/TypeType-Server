package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.findCachedMediaAt
import dev.typetype.server.services.playbackSegmentDurationMs
import dev.typetype.server.services.playbackSegmentStartMs
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat

internal data class SabrProgressiveWindowSegment(
    val sequence: Int,
    val response: SabrPlaybackWindowSegment,
    val hasReadableMedia: Boolean,
)

internal data class SabrProgressiveWindowAppend(
    val nextSequence: Int,
    val coveredEndMs: Long,
    val hasReadableMedia: Boolean,
)

internal fun MutableList<SabrPlaybackWindowSegment>.appendProgressiveWindowSegment(
    holder: SabrSessionHolder,
    format: YoutubeSabrFormat,
    predictedSequence: Int,
    targetMs: Long,
): SabrProgressiveWindowAppend? {
    val segment = holder.progressiveWindowSegment(format, predictedSequence, targetMs) ?: return null
    if (segment.sequence != predictedSequence) holder.session.streamState.jumpBufferedTo(format, segment.sequence)
    add(segment.response)
    return SabrProgressiveWindowAppend(
        segment.sequence + 1,
        segment.response.startMs + segment.response.durationMs,
        segment.hasReadableMedia,
    )
}

internal fun SabrSessionHolder.progressiveWindowSegment(
    format: YoutubeSabrFormat,
    predictedSequence: Int,
    targetMs: Long,
): SabrProgressiveWindowSegment? {
    val request = SabrSegmentRequest.media(format, predictedSequence)
    val exact = session.getReadableSegment(request)?.takeIf { it.covers(targetMs, this, format) }
    val actual = exact ?: session.findCachedMediaAt(format, targetMs, predictedSequence)
    actual?.let(::observeMediaSegment)
    val sequence = actual?.header?.sequenceNumber ?: predictedSequence
    val startMs = actual?.header?.startMs?.takeIf { it >= 0L }
        ?: playbackSegmentStartMs(format, sequence)
    val durationMs = actual?.header?.durationMs?.takeIf { it > 0L }
        ?: playbackSegmentDurationMs(format, sequence)
    if (startMs < 0L || durationMs <= 0L) return null
    return SabrProgressiveWindowSegment(
        sequence,
        SabrPlaybackWindowSegment(
            url = "${SabrPlaybackPaths.mediaBasePath(sessionToken)}/${format.itag}/segment/$sequence?generation=${activeGeneration()}",
            startMs = startMs,
            durationMs = durationMs,
        ),
        actual != null,
    )
}

private fun SabrMediaSegment.covers(
    targetMs: Long,
    holder: SabrSessionHolder,
    format: YoutubeSabrFormat,
): Boolean {
    val startMs = header.startMs.takeIf { it >= 0L }
        ?: holder.playbackSegmentStartMs(format, header.sequenceNumber)
    val durationMs = header.durationMs.takeIf { it > 0L }
        ?: holder.playbackSegmentDurationMs(format, header.sequenceNumber)
    return targetMs >= startMs - TIMING_TOLERANCE_MS && targetMs < startMs + durationMs
}

private const val TIMING_TOLERANCE_MS = 2L
