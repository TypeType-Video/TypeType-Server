package dev.typetype.server.services

import dev.typetype.server.sabr.SabrBufferedRange
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrStreamState
import org.slf4j.LoggerFactory

internal inline fun <T> withTargetedRequestShape(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    prepareSession: Boolean = true,
    block: () -> T,
): T {
    val companion = holder.companionFormat(request.format)
    val state = holder.session.streamState
    val requestStartMs = holder.playbackSegmentStartMs(request.format, request.sequenceNumber)
    val targetPlayerTimeMs = request.targetPlayerTimeMs(holder, requestStartMs)
    val ranges = listOf(request.targetRange(holder), companion.targetCompanionRange(holder, targetPlayerTimeMs))
    if (prepareSession) holder.session.prepareForMediaSegment(request)
    state.setPlayerTimeMs(targetPlayerTimeMs)
    state.setRequestTrackMode(request.trackMode(), true, true)
    state.setSelectVideoFormatBeforeAudio(request.format.isAudio)
    state.setBufferedRangesOverride(ranges)
    logger.info(
        "sabr_target_shape videoId={} request={}:{} playerMs={} trackMode={} ranges={}",
        holder.key.videoId,
        request.format.itag,
        request.sequenceNumber,
        targetPlayerTimeMs,
        request.trackMode(),
        ranges.joinToString(separator = ";") { it.summarize() },
    )
    return try {
        block()
    } finally {
        state.setBufferedRangesOverride(null)
        state.setActiveTrackTypes(holder.isVideoActive(), holder.isAudioActive())
        state.setSelectVideoFormatBeforeAudio(holder.playerTimeMs() > SEEK_FORMAT_ORDER_MS)
    }
}

private fun SabrSessionHolder.companionFormat(format: YoutubeSabrFormat): YoutubeSabrFormat =
    if (format.isAudio) videoFormat else audioFormat

private fun SabrSegmentRequest.trackMode(): Int =
    if (format.isAudio) YoutubeSabrStreamState.TRACK_MODE_AUDIO_ONLY else YoutubeSabrStreamState.TRACK_MODE_VIDEO_ONLY

private fun SabrSegmentRequest.targetRange(holder: SabrSessionHolder): SabrBufferedRange =
    format.bufferedRange(holder, (sequenceNumber - 1).coerceAtLeast(0))

private fun YoutubeSabrFormat.targetCompanionRange(holder: SabrSessionHolder, targetPlayerTimeMs: Long): SabrBufferedRange =
    bufferedRange(holder, (holder.playbackStartSequence(this, targetPlayerTimeMs) - 1).coerceAtLeast(0))

private fun SabrSegmentRequest.targetPlayerTimeMs(holder: SabrSessionHolder, startMs: Long): Long {
    val playerTimeMs = holder.playerTimeMs()
    val endMs = holder.playbackSegmentEndMs(format, sequenceNumber)
    if (endMs > startMs && playerTimeMs >= startMs && playerTimeMs < endMs) return playerTimeMs
    if (endMs > startMs + 1L) return minOf(startMs + TARGET_SEGMENT_OFFSET_MS, endMs - 1L)
    return startMs
}

private fun YoutubeSabrFormat.bufferedRange(holder: SabrSessionHolder, bufferedSequence: Int): SabrBufferedRange {
    val endMs = holder.playbackSegmentEndMs(this, bufferedSequence)
    val durationMs = endMs.takeIf { it > 0L } ?: 1L
    return SabrBufferedRange(
        itag,
        lastModified,
        xtags,
        0L,
        durationMs,
        if (bufferedSequence > 0) 1 else 0,
        bufferedSequence,
        TIMESCALE,
    )
}

private const val SEEK_FORMAT_ORDER_MS = 1_000L
private const val TARGET_SEGMENT_OFFSET_MS = 1_000L
private const val TIMESCALE = 1_000

private val logger = LoggerFactory.getLogger("SabrTargetRequestShape")
