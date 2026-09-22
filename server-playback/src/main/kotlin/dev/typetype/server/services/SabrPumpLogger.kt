package dev.typetype.server.services

import dev.typetype.server.sabr.SabrSegmentRequest
import org.slf4j.LoggerFactory

object SabrPumpLogger {
    private val logger = LoggerFactory.getLogger(SabrSessionPumpLoop::class.java)

    fun start(holder: SabrSessionHolder, event: String, request: SabrSegmentRequest?): Unit {
        logger.info(
            "sabr_pump event={}_start videoId={} request={} state={} requestNumber={} edgeMs={} readerHeadMs={} readerTailMs={} cachedBytes={}",
            event,
            holder.key.videoId,
            request?.summary(),
            holder.playbackState(),
            holder.session.requestNumber,
            holder.session.streamState.getMinBufferedEndMs(),
            holder.readerHeadMs(),
            holder.readerTailMs(),
            holder.session.cachedBytes,
        )
    }

    fun finish(holder: SabrSessionHolder, event: String, request: SabrSegmentRequest?, pumped: Int): Unit {
        logger.info(
            "sabr_pump event={}_finish videoId={} request={} pumped={} cached={} state={} requestNumber={} edgeMs={} readerHeadMs={} readerTailMs={} cachedBytes={}",
            event,
            holder.key.videoId,
            request?.summary(),
            pumped,
            request?.let { holder.session.getCachedSegment(it) != null },
            holder.playbackState(),
            holder.session.requestNumber,
            holder.session.streamState.getMinBufferedEndMs(),
            holder.readerHeadMs(),
            holder.readerTailMs(),
            holder.session.cachedBytes,
        )
    }

    fun failure(holder: SabrSessionHolder, error: Exception): Unit {
        logger.warn(
            "sabr_pump event=round_failed videoId={} state={} requestNumber={} edgeMs={} cachedBytes={} errorType={} error={}",
            holder.key.videoId,
            holder.playbackState(),
            holder.session.requestNumber,
            holder.session.streamState.getMinBufferedEndMs(),
            holder.session.cachedBytes,
            error.javaClass.simpleName,
            error.message,
            error,
        )
    }

    fun expired(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        recoverable: Boolean,
        registeredAtMs: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
        expectedDelayMs: Long? = null,
        reason: String = "deadline",
        attempts: Int = 0,
        lastAttemptDurationMs: Long = -1L,
        event: String = "demand_expired",
    ): Unit {
        val liveHeadTimeMs = holder.livePlaybackSnapshot()?.headTimeMs ?: -1L
        val segmentEndMs = runCatching { holder.playbackSegmentEndMs(request.format, request.sequenceNumber) }
            .getOrDefault(-1L)
        val bufferedEdgeMs = holder.session.streamState.getMinBufferedEndMs()
        val ageMs = registeredAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: -1L
        logger.warn(
            "sabr_pump event={} videoId={} request={} track={} sequence={} recoverable={} ageMs={} registeredAtMs={} expectedDelayMs={} liveHeadTimeMs={} segmentEndMs={} bufferedEdgeMs={} lastAttemptDurationMs={} attempts={} reason={} state={} requestNumber={} readerHeadMs={} readerTailMs={} cachedBytes={}",
            event,
            holder.key.videoId,
            request.summary(),
            if (request.format.isAudio) "audio" else "video",
            request.sequenceNumber,
            recoverable,
            ageMs,
            registeredAtMs ?: -1L,
            expectedDelayMs ?: -1L,
            liveHeadTimeMs,
            segmentEndMs,
            bufferedEdgeMs,
            lastAttemptDurationMs,
            attempts,
            reason,
            holder.playbackState(),
            holder.session.requestNumber,
            holder.readerHeadMs(),
            holder.readerTailMs(),
            holder.session.cachedBytes,
        )
    }

    fun recovery(holder: SabrSessionHolder, action: SabrDemandRecoveryAction, request: SabrSegmentRequest): Unit {
        logger.info(
            "sabr_pump event=demand_recovery videoId={} request={} action={} requestNumber={} edgeMs={} readerHeadMs={} readerTailMs={} cachedBytes={}",
            holder.key.videoId,
            request.summary(),
            action,
            holder.session.requestNumber,
            holder.session.streamState.getMinBufferedEndMs(),
            holder.readerHeadMs(),
            holder.readerTailMs(),
            holder.session.cachedBytes,
        )
    }

    private fun SabrSegmentRequest.summary(): String = "${format.itag}:$sequenceNumber"
}
