package dev.typetype.server.services

import dev.typetype.server.sabr.SabrSegmentRequest
import org.slf4j.LoggerFactory

internal object SabrPumpLogger {
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

    fun expired(holder: SabrSessionHolder, request: SabrSegmentRequest, recoverable: Boolean): Unit {
        logger.warn(
            "sabr_pump event=demand_expired videoId={} request={} recoverable={} state={} requestNumber={} edgeMs={} readerHeadMs={} readerTailMs={} cachedBytes={}",
            holder.key.videoId,
            request.summary(),
            recoverable,
            holder.playbackState(),
            holder.session.requestNumber,
            holder.session.streamState.getMinBufferedEndMs(),
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
