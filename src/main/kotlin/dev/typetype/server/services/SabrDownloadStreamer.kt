package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.localization.Localization
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.OutputStream

internal class SabrDownloadStreamer(
    private val store: SabrSessionStore,
    private val pumpTimeoutMs: Long = PUMP_TIMEOUT_MS,
) {
    private val localization = Localization("en", "US")
    private val unauthorizedRecovery = SabrUnauthorizedResponseRecovery { holder ->
        store.refreshVideoPoToken(holder.key.videoId)
    }

    suspend fun stream(
        holder: SabrSessionHolder,
        output: OutputStream,
        range: SabrDownloadRange = SabrDownloadRange(),
    ) {
        val startedNs = System.nanoTime()
        val writer = SabrDownloadFrameWriter(output)
        var pumpCount = 0
        writer.start()
        val formats = selectedFormats(holder)
        writeInitializations(holder, formats, writer)
        val tracks = selectedTracks(holder, formats, range)
        discardBeforeRange(holder, tracks)
        var emptyRounds = 0
        while (!complete(holder, tracks)) {
            val before = drain(holder, tracks, writer)
            if (complete(holder, tracks)) break
            val target = nextMissingRequest(holder, tracks)
                ?: throw IOException("SABR download could not identify the next missing segment")
            val playerTimeMs = holder.session.streamState
                .getSegmentStartMs(target.format, target.sequenceNumber)
                .coerceAtLeast(0L)
            val pumped = pump(holder, playerTimeMs, target)
            pumpCount++
            val after = drain(holder, tracks, writer)
            if (holder.session.cachedBytes > MAX_SESSION_CACHE_BYTES) {
                throw IOException("SABR download exceeded its bounded media cache")
            }
            emptyRounds = if (before + after + pumped > 0) 0 else emptyRounds + 1
            if (emptyRounds > MAX_EMPTY_ROUNDS) {
                val status = SabrUnauthorizedResponseRecovery.latestUnauthorizedStatus(
                    holder.session.diagnosticTrace,
                )
                logger.warn(
                    "sabr_download_stall videoId={} part={}/{} request={} upstreamStatus={}",
                    holder.key.videoId,
                    range.part + 1,
                    range.parts,
                    holder.session.requestNumber,
                    status,
                )
                throw IOException("SABR download stalled without receiving media")
            }
            if (emptyRounds > 0) delay(EMPTY_ROUND_DELAY_MS)
        }
        writer.finish()
        logger.info(
            "sabr_download videoId={} part={}/{} tracks={} durationMs={} pumps={} upstreamBytes={} peakResponseBytes={}",
            holder.key.videoId,
            range.part + 1,
            range.parts,
            tracks.size,
            (System.nanoTime() - startedNs) / 1_000_000,
            pumpCount,
            holder.session.totalResponseBytes,
            holder.session.maxResponseBytes,
        )
    }

    private suspend fun writeInitializations(
        holder: SabrSessionHolder,
        formats: List<YoutubeSabrFormat>,
        writer: SabrDownloadFrameWriter,
    ) {
        for (format in formats) {
            val data = SabrDownloadInitialization.fetch(store, holder, format)
                ?: throw IOException("Missing SABR initialization for itag ${format.itag}")
            writer.initialization(format.itag, data)
            holder.session.discardCachedSegment(SabrSegmentRequest.initialization(format))
        }
    }

    private fun drain(
        holder: SabrSessionHolder,
        tracks: List<DownloadTrack>,
        writer: SabrDownloadFrameWriter,
    ): Int {
        var written = 0
        for (track in tracks) {
            while (true) {
                if (track.endSequenceExclusive?.let { track.nextSequence >= it } == true) break
                val request = SabrSegmentRequest.media(track.format, track.nextSequence)
                val segment = holder.session.getCachedSegment(request) ?: break
                segment.openStream().use {
                    writer.media(track.format.itag, track.nextSequence, segment.length, it)
                }
                holder.session.discardCachedSegment(request)
                track.nextSequence++
                written++
            }
        }
        return written
    }

    private fun discardBeforeRange(holder: SabrSessionHolder, tracks: List<DownloadTrack>) {
        for (track in tracks) {
            for (sequence in 1 until track.nextSequence) {
                holder.session.discardCachedSegment(SabrSegmentRequest.media(track.format, sequence))
            }
        }
    }

    private fun nextMissingRequest(
        holder: SabrSessionHolder,
        tracks: List<DownloadTrack>,
    ): SabrSegmentRequest? = tracks
        .asSequence()
        .filterNot { track -> track.endSequenceExclusive?.let { track.nextSequence >= it } == true }
        .map { track -> SabrSegmentRequest.media(track.format, track.nextSequence) }
        .filter { request ->
            holder.session.getCachedSegment(request) == null && !holder.session.isBeyondEnd(request)
        }
        .minByOrNull { request ->
            holder.session.streamState.getSegmentStartMs(request.format, request.sequenceNumber)
        }

    private suspend fun pump(
        holder: SabrSessionHolder,
        playerTimeMs: Long,
        target: SabrSegmentRequest,
    ): Int {
        val pumped = withContext(Dispatchers.IO) {
            withTimeoutOrNull(pumpTimeoutMs) {
                runInterruptible {
                    holder.withPlayerContext {
                        prepareForForwardJump(target, playerTimeMs.coerceAtLeast(0L))
                        pumpOnceStreamingUntilCached(localization, target)
                    }
                }
            }
        } ?: throw IOException("SABR download upstream pump timed out")
        return pumped.also { unauthorizedRecovery.verify(holder) }
    }

    private fun complete(holder: SabrSessionHolder, tracks: List<DownloadTrack>): Boolean =
        tracks.all { track ->
            if (track.endSequenceExclusive?.let { track.nextSequence >= it } == true) return@all true
            val request = SabrSegmentRequest.media(track.format, track.nextSequence)
            holder.session.getCachedSegment(request) == null &&
                (holder.session.streamState.isComplete(track.format) || holder.session.isBeyondEnd(request))
        }

    private fun selectedTracks(
        holder: SabrSessionHolder,
        formats: List<YoutubeSabrFormat>,
        range: SabrDownloadRange,
    ): List<DownloadTrack> = formats.map { format ->
        DownloadTrack(
            format,
            range.startSequence(holder.session.streamState, format),
            range.endSequenceExclusive(holder.session.streamState, format),
        )
    }

    private fun selectedFormats(holder: SabrSessionHolder): List<YoutubeSabrFormat> =
        buildList {
            if (holder.isAudioActive()) add(holder.audioFormat)
            if (holder.isVideoActive()) add(holder.videoFormat)
        }

    private data class DownloadTrack(
        val format: YoutubeSabrFormat,
        var nextSequence: Int,
        val endSequenceExclusive: Int?,
    )

    private companion object {
        val logger = LoggerFactory.getLogger(SabrDownloadStreamer::class.java)
        const val MAX_EMPTY_ROUNDS = 12
        const val EMPTY_ROUND_DELAY_MS = 50L
        const val MAX_SESSION_CACHE_BYTES = 64L * 1024 * 1024
        const val PUMP_TIMEOUT_MS = 30_000L
    }
}
