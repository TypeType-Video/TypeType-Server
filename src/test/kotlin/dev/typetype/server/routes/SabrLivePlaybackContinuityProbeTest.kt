package dev.typetype.server.routes

import dev.typetype.server.services.NewPipeInitializer
import dev.typetype.server.services.SabrInfoFetcher
import dev.typetype.server.services.SabrPlaybackSegmentResult
import dev.typetype.server.services.SabrPlaybackSessionService
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.TypetypeTokenSabrTokenClient
import dev.typetype.server.services.TypetypeTokenYoutubeSessionClient
import dev.typetype.server.services.livePlaybackSnapshot
import dev.typetype.server.services.liveRetryAfterMs
import dev.typetype.server.services.sabrProbeAudioItag
import dev.typetype.server.services.sabrProbeTokenServiceUrl
import dev.typetype.server.services.sabrProbeVideoId
import dev.typetype.server.services.sabrProbeVideoItags
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat

@EnabledIfSystemProperty(named = "sabr.probe", matches = "true")
@Tag("network")
class SabrLivePlaybackContinuityProbeTest {
    @Test
    fun `measures live playback buffer continuity`(): Unit = runBlocking {
        val videoId = sabrProbeVideoId()
        val tokenServiceUrl = sabrProbeTokenServiceUrl()
        NewPipeInitializer.init(tokenServiceUrl)
        val store = SabrSessionStore(tokenServiceUrl = tokenServiceUrl)
        try {
            val prepared = SabrInfoFetcher(
                TypetypeTokenSabrTokenClient(tokenServiceUrl),
                TypetypeTokenYoutubeSessionClient(tokenServiceUrl),
            ).fetchInfo(videoId) ?: error("Missing SABR player metadata")
            val audio = prepared.info.formats.first { it.isAudio && it.itag == sabrProbeAudioItag() }
            val video = prepared.info.formats.first { it.isVideo && it.itag == sabrProbeVideoItags().first() }
            val service = SabrPlaybackSessionService(store)
            val preparation = service.prepare(
                videoId = videoId,
                userId = "live-continuity-probe",
                prepared = prepared,
                audio = audio,
                video = video,
                startTimeMs = 0L,
                isLive = true,
            )
            val holder = preparation.holder
            val builder = SabrPlaybackWindowBuilder(store)
            val buffer = BufferState()
            val durationMs = probeDurationMs()
            val liveHead = requireNotNull(holder.livePlaybackSnapshot()).headSequence.toInt()
            for (format in listOf(audio, video)) {
                val cached = (liveHead - 30..liveHead + 2).mapNotNull { sequence ->
                    store.cachedSegment(holder, SabrSegmentRequest.media(format, sequence))
                        ?.let { "$sequence:${it.startMs}+${it.durationMs}" }
                }
                println("WARMED itag=${format.itag} head=$liveHead cached=${cached.joinToString()}")
            }

            refresh(builder, store, service, holder, audio, video, preparation.startTimeMs, buffer)
            val startedAt = System.nanoTime()
            val playbackStartMs = buffer.startMs
            val initialCachedBytes = holder.session.cachedBytes
            val initialHeapBytes = usedHeapBytes()
            val initialCpuNanos = currentCpuNanos()
            var maxCachedBytes = initialCachedBytes
            var maxHeapBytes = initialHeapBytes
            var stalls = 0
            var refreshes = 1
            while (elapsedMs(startedAt) < durationMs) {
                maxCachedBytes = maxOf(maxCachedBytes, holder.session.cachedBytes)
                maxHeapBytes = maxOf(maxHeapBytes, usedHeapBytes())
                val playerTimeMs = playbackStartMs + elapsedMs(startedAt)
                val aheadMs = buffer.endMs - playerTimeMs
                if (aheadMs <= 0L) {
                    stalls++
                    println("STALL elapsedMs=${elapsedMs(startedAt)} playerMs=$playerTimeMs bufferEndMs=${buffer.endMs}")
                }
                if (aheadMs < REFRESH_THRESHOLD_MS) {
                    val refreshWaitMs = refresh(builder, store, service, holder, audio, video, playerTimeMs, buffer)
                    if (refreshWaitMs > aheadMs.coerceAtLeast(0L)) {
                        stalls++
                        println("STALL refreshWaitMs=$refreshWaitMs availableAheadMs=${aheadMs.coerceAtLeast(0L)}")
                    }
                    refreshes++
                }
                delay(TICK_MS)
            }
            println(
                "CONTINUITY durationMs=$durationMs refreshes=$refreshes stalls=$stalls " +
                    "finalAheadMs=${buffer.endMs - (playbackStartMs + elapsedMs(startedAt))} " +
                    "requests=${holder.session.requestNumber} formats=${video.itag}/${audio.itag} " +
                    "cachedBytes=$initialCachedBytes..${holder.session.cachedBytes} maxCachedBytes=$maxCachedBytes " +
                    "heapBytes=$initialHeapBytes..${usedHeapBytes()} maxHeapBytes=$maxHeapBytes " +
                    "cpuMs=${(currentCpuNanos() - initialCpuNanos).coerceAtLeast(0L) / 1_000_000L}"
            )
            assertEquals(0, stalls)
        } finally {
            store.release()
        }
    }

    private suspend fun refresh(
        builder: SabrPlaybackWindowBuilder,
        store: SabrSessionStore,
        service: SabrPlaybackSessionService,
        holder: dev.typetype.server.services.SabrSessionHolder,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        playerTimeMs: Long,
        buffer: BufferState,
    ): Long {
        var polls = 0
        val startedAt = System.nanoTime()
        while (polls < MAX_WINDOW_POLLS) {
            polls++
            holder.touch()
            holder.setPlayerTimeMs(playerTimeMs)
            val request = SabrPlaybackWindowRequest(
                generation = holder.activeGeneration(),
                playerTimeMs = playerTimeMs,
                videoItag = video.itag,
                audioItag = audio.itag,
                bufferGoalMs = BUFFER_GOAL_MS,
                bufferedRanges = buffer.ranges(audio.itag, video.itag, playerTimeMs),
            )
            val window = builder.build(holder, request)
            if (window.isReady) {
                append(service, holder, audio, window.response.audio, buffer)
                append(service, holder, video, requireNotNull(window.response.video), buffer)
                buffer.startMs = maxOf(buffer.startMs, window.response.startTimeMs)
                println(
                    "READY polls=$polls waitMs=${elapsedMs(startedAt)} playerMs=$playerTimeMs " +
                        "bufferEndMs=${buffer.endMs} aheadMs=${buffer.endMs - playerTimeMs} " +
                        "request=${holder.session.requestNumber}"
                )
                return elapsedMs(startedAt)
            }
            window.blockedRequests.forEach {
                store.requestSegmentDemand(holder, it, holder.activeGeneration())
            }
            val retryMs = holder.liveRetryAfterMs(window.blockedRequests)
            println(
                "PENDING polls=$polls retryMs=$retryMs playerMs=$playerTimeMs " +
                    "edgeMs=${holder.session.streamState.getMinBufferedEndMs()} " +
                    "state=${holder.playbackState()} terminal=${holder.terminalFailure()} " +
                    "network=${holder.networkFailure()} " +
                    "audioObserved=${holder.observedSummary(audio)} " +
                    "videoObserved=${holder.observedSummary(video)} " +
                    "audioWindow=${window.response.audio.summary()} " +
                    "videoWindow=${window.response.video?.summary()} " +
                    "blocked=${window.blockedRequests.joinToString { "${it.format.itag}:${it.sequenceNumber}" }}"
            )
            delay(retryMs)
        }
        println("TRACE ${holder.session.diagnosticTrace.takeLast(MAX_TRACE_CHARS)}")
        error(
            "Playback window did not become ready after $polls polls: " +
                "state=${holder.playbackState()} terminal=${holder.terminalFailure()} " +
                "network=${holder.networkFailure()}"
        )
    }

    private suspend fun append(
        service: SabrPlaybackSessionService,
        holder: dev.typetype.server.services.SabrSessionHolder,
        format: YoutubeSabrFormat,
        track: SabrPlaybackWindowTrack,
        buffer: BufferState,
    ) {
        for (segment in track.segments) {
            val sequence = requireNotNull(SEGMENT_SEQUENCE.find(segment.url)?.groupValues?.get(1)?.toIntOrNull())
            val result = service.fetchMedia(holder, format, sequence, 2_000L, holder.activeGeneration())
            check(result is SabrPlaybackSegmentResult.Ready) { "Segment ${format.itag}:$sequence was not ready: $result" }
            buffer.update(format.isAudio, segment.startMs, segment.startMs + segment.durationMs)
        }
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000L

    private fun usedHeapBytes(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    private fun currentCpuNanos(): Long =
        ProcessHandle.current().info().totalCpuDuration().orElse(java.time.Duration.ZERO).toNanos()

    private fun dev.typetype.server.services.SabrSessionHolder.observedSummary(format: YoutubeSabrFormat): String {
        val header = observedMediaSegment(format)?.header ?: return "none"
        return "${header.sequenceNumber}:${header.startMs}+${header.durationMs}"
    }

    private fun SabrPlaybackWindowTrack.summary(): String =
        segments.joinToString(prefix = "[", postfix = "]") {
            "${SEGMENT_SEQUENCE.find(it.url)?.groupValues?.get(1)}:${it.startMs}+${it.durationMs}"
        }

    private fun probeDurationMs(): Long =
        System.getenv("SABR_PROBE_DURATION_MS")?.toLongOrNull()?.coerceAtLeast(10_000L) ?: 20_000L

    private data class BufferState(
        var startMs: Long = 0L,
        var audioEndMs: Long = 0L,
        var videoEndMs: Long = 0L,
    ) {
        val endMs: Long
            get() = minOf(audioEndMs, videoEndMs)

        fun update(audio: Boolean, startMs: Long, endMs: Long) {
            if (this.startMs == 0L) this.startMs = startMs
            if (audio) audioEndMs = maxOf(audioEndMs, endMs) else videoEndMs = maxOf(videoEndMs, endMs)
        }

        fun ranges(audioItag: Int, videoItag: Int, playerTimeMs: Long): List<SabrPlaybackBufferedRange> {
            if (endMs <= playerTimeMs) return emptyList()
            return listOf(
                SabrPlaybackBufferedRange(audioItag, startMs, audioEndMs),
                SabrPlaybackBufferedRange(videoItag, startMs, videoEndMs),
            )
        }
    }

    private companion object {
        val SEGMENT_SEQUENCE = Regex("/segment/(\\d+)")
        const val BUFFER_GOAL_MS = 8_000L
        const val REFRESH_THRESHOLD_MS = 5_333L
        const val TICK_MS = 250L
        const val MAX_WINDOW_POLLS = 30
        const val MAX_TRACE_CHARS = 8_000
    }
}
