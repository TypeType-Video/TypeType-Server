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
    val sequence: Int,
    val coveredEndMs: Long,
    val hasReadableMedia: Boolean,
) {
    val nextSequence: Int get() = sequence + 1
}

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
        segment.sequence,
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
    val sequence = actual?.header?.sequenceNumber ?: pendingSequence(format, predictedSequence, targetMs)
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

// The index rounds start and duration on their own, so its end can sit past the media header's end: the buffered end
// then maps back to a segment the player holds, and a demand for a cached segment is dropped, which stalls playback.
private fun SabrSessionHolder.pendingSequence(format: YoutubeSabrFormat, predictedSequence: Int, targetMs: Long): Int {
    val held = session.getCachedSegment(SabrSegmentRequest.media(format, predictedSequence)) ?: return predictedSequence
    return if (targetMs >= held.endMs(this, format)) predictedSequence + 1 else predictedSequence
}

private fun SabrMediaSegment.covers(
    targetMs: Long,
    holder: SabrSessionHolder,
    format: YoutubeSabrFormat,
): Boolean = targetMs >= startMs(holder, format) - TIMING_TOLERANCE_MS && targetMs < endMs(holder, format)

private fun SabrMediaSegment.startMs(holder: SabrSessionHolder, format: YoutubeSabrFormat): Long =
    header.startMs.takeIf { it >= 0L } ?: holder.playbackSegmentStartMs(format, header.sequenceNumber)

private fun SabrMediaSegment.endMs(holder: SabrSessionHolder, format: YoutubeSabrFormat): Long =
    startMs(holder, format) + (
        header.durationMs.takeIf { it > 0L } ?: holder.playbackSegmentDurationMs(format, header.sequenceNumber)
    )

private const val TIMING_TOLERANCE_MS = 2L
