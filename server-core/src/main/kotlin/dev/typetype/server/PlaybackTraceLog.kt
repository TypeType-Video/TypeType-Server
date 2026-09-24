package dev.typetype.server

object PlaybackTraceLog {
    private val logger = System.getLogger(PlaybackTraceLog::class.java.name)

    fun record(event: String, fields: String = "") {
        record(currentPlaybackTraceId(), currentRequestId(), event, fields)
    }

    fun record(traceId: String?, requestId: String?, event: String, fields: String = "") {
        if (traceId == null) return
        logger.log(
            System.Logger.Level.INFO,
            "playback_trace traceId={0} requestId={1} event={2} {3}",
            traceId, requestId ?: "unknown", event, fields,
        )
    }
}
