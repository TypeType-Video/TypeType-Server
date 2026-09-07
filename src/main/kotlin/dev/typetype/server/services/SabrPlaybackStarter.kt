package dev.typetype.server.services

import dev.typetype.server.sabr.SabrSegmentRequest
import org.slf4j.LoggerFactory

internal object SabrPlaybackStarter {
    fun start(
        store: SabrSessionStore,
        holder: SabrSessionHolder,
        startTimeMs: Long,
        audioOnly: Boolean,
        startPump: Boolean,
    ): SabrPlaybackPreparation {
        val startedAt = System.currentTimeMillis()
        holder.setActiveTracks(videoActive = !audioOnly, audioActive = true)
        holder.setPlayerTimeMs(startTimeMs)
        holder.session.streamState.setSelectVideoFormatBeforeAudio(startTimeMs > SABR_SEEK_FORMAT_ORDER_MS)
        if (startTimeMs > SABR_SEEK_FORMAT_ORDER_MS) holder.anchorReaderPositions(startTimeMs)
        if (startTimeMs > 0L) {
            holder.setRequestedSeekTimeMs(startTimeMs)
            holder.requestPlaybackReposition(startTimeMs, holder.activeGeneration())
        }
        if (startPump) store.startPump(holder)
        logger.info(
            "sabr_playback_prepare videoId={} startTimeMs={} elapsedMs={} ready=false",
            holder.key.videoId,
            startTimeMs,
            System.currentTimeMillis() - startedAt,
        )
        return SabrPlaybackPreparation(holder, startTimeMs, ready = false)
    }

    private val logger = LoggerFactory.getLogger(SabrPlaybackStarter::class.java)
}

internal fun SabrSessionHolder.requestPlaybackReposition(playerTimeMs: Long, generation: Long): Unit {
    val targetFormat = if (isVideoActive()) videoFormat else audioFormat
    val request = SabrSegmentRequest.media(targetFormat, playbackStartSequence(targetFormat, playerTimeMs))
    val companion = audioFormat.takeIf { isVideoActive() }?.let { format ->
        SabrSegmentRequest.media(format, playbackStartSequence(format, playerTimeMs))
    }
    val missing = repositionTargets(listOfNotNull(request, companion), playerTimeMs, generation)
    if (missing.isEmpty()) return
    missing.forEach { requestSegmentDemand(it, generation) }
    if (livePlaybackSnapshot()?.active == true) return
    val anchor = missing.first()
    val startMs = playbackSegmentStartMs(anchor.format, anchor.sequenceNumber)
    if (startMs < session.streamState.getMinBufferedEndMs()) {
        requestRefetch(anchor)
    } else {
        requestForwardSeek(anchor)
    }
}

internal const val SABR_SEEK_FORMAT_ORDER_MS = 1_000L
