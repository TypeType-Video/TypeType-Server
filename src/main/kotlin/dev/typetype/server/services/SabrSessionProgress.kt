package dev.typetype.server.services

import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.YoutubeSabrFormat

internal fun SabrSessionHolder.markServed(segment: SabrMediaSegment): Unit {
    markServed(segment, activeGeneration())
}

internal fun SabrSessionHolder.markServed(segment: SabrMediaSegment, generation: Long): Unit {
    if (!segment.header.isInitSegment) {
        observeMediaSegment(segment)
        val format = if (audioFormat.itag == segment.header.itag) audioFormat else videoFormat
        setReaderPosition(format, playbackSegmentEndMs(format, segment.header.sequenceNumber), generation)
        setLastServedSequence(segment.header.itag, segment.header.sequenceNumber, generation)
        evictCachedSegmentsBefore(readerTailMs() - SabrPumpPolicy.backBufferMs(this))
    }
}

internal fun SabrSessionHolder.markPrepared(segment: SabrMediaSegment): Unit {
    if (!segment.header.isInitSegment) {
        observeMediaSegment(segment)
        val format = if (audioFormat.itag == segment.header.itag) audioFormat else videoFormat
        setReaderPosition(format, playbackSegmentEndMs(format, segment.header.sequenceNumber))
    }
}

internal fun SabrSessionHolder.markServed(segment: CachedSabrSegment): Unit {
    markServed(segment, activeGeneration())
}

internal fun SabrSessionHolder.markServed(segment: CachedSabrSegment, generation: Long): Unit {
    if (!segment.init) {
        val format = if (audioFormat.itag == segment.itag) audioFormat else videoFormat
        setReaderPosition(format, segment.startMs + segment.durationMs, generation)
        setLastServedSequence(segment.itag, segment.sequence, generation)
        evictCachedSegmentsBefore(readerTailMs() - SabrPumpPolicy.backBufferMs(this))
    }
}

internal fun SabrSessionHolder.shouldSend(segment: SabrMediaSegment): Boolean {
    if (segment.header.isInitSegment) return true
    val lastSequence = lastServedSequence(formatForItag(segment.header.itag)) ?: return true
    return segment.header.sequenceNumber > lastSequence
}

internal fun SabrSessionHolder.shouldSend(segment: CachedSabrSegment): Boolean {
    if (segment.init) return true
    val lastSequence = lastServedSequence(formatForItag(segment.itag)) ?: return true
    return segment.sequence > lastSequence
}

private fun SabrSessionHolder.formatForItag(itag: Int): YoutubeSabrFormat =
    if (audioFormat.itag == itag) audioFormat else videoFormat

internal fun bothFormatsKnown(holder: SabrSessionHolder): Boolean {
    val state = holder.session.streamState
    return state.getEndSegment(holder.audioFormat) > 0L &&
        state.getEndSegment(holder.videoFormat) > 0L &&
        state.getSegmentEndMs(holder.audioFormat, 1) > 0L &&
        state.getSegmentEndMs(holder.videoFormat, 1) > 0L
}
