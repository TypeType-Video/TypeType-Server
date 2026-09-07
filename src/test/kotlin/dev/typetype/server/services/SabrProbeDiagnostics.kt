package dev.typetype.server.services

import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat

internal fun printSabrProbeFormat(label: String, format: YoutubeSabrFormat): Unit {
    println(
        "$label format itag=${format.itag} audio=${format.isAudio} video=${format.isVideo} " +
            "size=${format.width}x${format.height} bitrate=${format.bitrate} mime=${format.mimeType} " +
            "quality=${format.qualityLabel} audioTrack=${format.audioTrackId} " +
            "xtags=${format.xtags} drc=${format.isDrc} original=${format.isOriginalAudio} " +
            "approxDurationMs=${format.approxDurationMs}"
    )
}

internal fun printSabrProbeFetch(
    label: String,
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    result: SabrProbeFetchResult,
): Unit {
    val outcome = when {
        result.timedOut -> "timeout"
        result.error != null -> "error"
        result.segment == null -> "miss"
        else -> "hit"
    }
    println(
        "$label request ${sabrProbeRequestSummary(holder, request)} " +
            "elapsedMs=${result.elapsedMs} outcome=$outcome"
    )
    result.segment?.let { println("$label header ${sabrProbeSegmentHeader(it)}") }
    result.error?.let { println("$label error ${it.javaClass.simpleName}: ${it.message}") }
}

internal fun sabrProbeSegmentHeader(segment: SabrMediaSegment): String {
    val header = segment.header
    return "headerId=${header.headerId} videoId=${header.videoId} itag=${header.itag} " +
        "seq=${header.sequenceNumber} init=${header.isInitSegment} startMs=${header.startMs} " +
        "durationMs=${header.durationMs} bitrateBps=${header.bitrateBps} " +
        "contentLength=${header.contentLength} bytes=${segment.length} summary=${header.summarize()}"
}

internal fun sabrProbeRequestSummary(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
): String {
    val expectedStartMs = if (request.isInitializationSegment) {
        -1L
    } else {
        holder.session.streamState.getSegmentStartMs(request.format, request.sequenceNumber)
    }
    val expectedEndMs = if (request.isInitializationSegment) {
        -1L
    } else {
        holder.session.streamState.getSegmentEndMs(request.format, request.sequenceNumber)
    }
    return "itag=${request.format.itag} seq=${request.sequenceNumber} init=${request.isInitializationSegment} " +
        "expectedStartMs=$expectedStartMs expectedEndMs=$expectedEndMs " +
        "edgeMs=${holder.session.streamState.getMinBufferedEndMs()} " +
        "requestNumber=${holder.session.requestNumber} cachedBytes=${holder.session.cachedBytes}"
}
