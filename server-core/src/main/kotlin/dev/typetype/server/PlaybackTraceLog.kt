package dev.typetype.server

import org.slf4j.LoggerFactory

object PlaybackTraceLog {
    private val logger = LoggerFactory.getLogger(PlaybackTraceLog::class.java)

    fun record(event: String, fields: String = "") {
        record(currentPlaybackTraceId(), currentRequestId(), event, fields)
    }

    fun record(traceId: String?, requestId: String?, event: String, fields: String = "") {
        if (traceId == null) return
        logger.info("playback_trace traceId={} requestId={} event={} {}", traceId, requestId ?: "unknown", event, fields)
    }
}
