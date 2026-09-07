package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrStreamState

internal data class SabrDownloadRange(
    val part: Int = 0,
    val parts: Int = 1,
) {
    init {
        require(parts in 1..MAX_PARTS) { "parts must be between 1 and $MAX_PARTS" }
        require(part in 0 until parts) { "part must be between 0 and parts - 1" }
    }

    fun startTimeMs(audio: YoutubeSabrFormat, video: YoutubeSabrFormat, audioOnly: Boolean): Long {
        if (part == 0) return 0L
        val durationMs = if (audioOnly) audio.approxDurationMs else maxOf(audio.approxDurationMs, video.approxDurationMs)
        return durationMs.coerceAtLeast(0L) * part / parts
    }

    fun startSequence(state: YoutubeSabrStreamState, format: YoutubeSabrFormat): Int =
        boundarySequence(state, format, part)

    fun endSequenceExclusive(state: YoutubeSabrStreamState, format: YoutubeSabrFormat): Int? {
        if (parts == 1) return null
        if (part + 1 < parts) return boundarySequence(state, format, part + 1)
        val end = state.getEndSegment(format)
        return end.takeIf { it in 1 until Int.MAX_VALUE }?.toInt()?.plus(1)
    }

    private fun boundarySequence(
        state: YoutubeSabrStreamState,
        format: YoutubeSabrFormat,
        boundaryPart: Int,
    ): Int {
        if (parts == 1 || boundaryPart == 0) return 1
        val durationMs = format.approxDurationMs.coerceAtLeast(0L)
        require(durationMs > 0L) { "SABR duration is required for multipart downloads" }
        val boundaryMs = durationMs * boundaryPart / parts
        return state.getSegmentNumberAtOrAfterTimeMs(format, boundaryMs).coerceAtLeast(1)
    }

    private companion object {
        const val MAX_PARTS = 12
    }
}
