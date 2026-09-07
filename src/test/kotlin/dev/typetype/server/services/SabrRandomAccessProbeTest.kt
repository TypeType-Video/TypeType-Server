package dev.typetype.server.services

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.schabi.newpipe.extractor.NewPipe
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.stream.StreamInfo

@EnabledIfSystemProperty(named = "sabr.probe", matches = "true")
@Tag("network")
class SabrRandomAccessProbeTest {
    private val tokenServiceUrl: String =
        sabrProbeTokenServiceUrl()

    @Test
    fun `fetches post seek stateful responses`(): Unit = runBlocking {
        NewPipeInitializer.init()
        val videoId = sabrProbeVideoId()
        val videoItags = sabrProbeVideoItags()
        val audioItag = sabrProbeAudioItag()
        val playerTimeMs = sabrProbePlayerTimeMs()
        val timeoutMs = sabrProbeTimeoutMs()
        val store = SabrSessionStore(tokenServiceUrl = tokenServiceUrl)
        try {
            println(
                "config videoId=$videoId playerTimeMs=$playerTimeMs audioItag=$audioItag " +
                    "videoItags=${videoItags.joinToString(",")} timeoutMs=$timeoutMs " +
                    "contract=stateful-pump"
            )
            if (System.getenv("SABR_PROBE_EXTRACT_FIRST") != "false") {
                store.rememberExtractedInfo(videoId, extractSabrInfo(videoId))
            }
            val prepared = store.fetchInfo(videoId, playerTimeMs, cachedFirst = true) ?: error("SABR probe failed")
            val info = prepared.info
            val audio = requireAudioFormat(info.formats, audioItag)
            printSabrProbeFormat("audio", audio)
            for (videoItag in videoItags) {
                val video = requireVideoFormat(info.formats, videoItag)
                printSabrProbeFormat("video", video)
                val startedAt = System.nanoTime()
                val preparation = SabrPlaybackSessionService(store).prepare(
                    videoId = videoId,
                    userId = "sabr-random-access-video-$videoItag",
                    prepared = prepared,
                    audio = audio,
                    video = video,
                    startTimeMs = playerTimeMs,
                )
                val holder = preparation.holder
                assertInitData(store, holder, audio)
                assertInitData(store, holder, video)
                holder.setActiveTracks(videoActive = true, audioActive = true)
                holder.setPlayerTimeMs(playerTimeMs)
                val requests = listOf(audio, video).map { format ->
                    SabrSegmentRequest.media(format, holder.playbackStartSequence(format, playerTimeMs))
                }
                requests.forEach { request ->
                    println("pump target[$videoItag] ${sabrProbeRequestSummary(holder, request)}")
                }
                val segments = withTimeoutOrNull(timeoutMs) {
                    requests.map { request ->
                        store.requestSegmentDemand(holder, request, holder.activeGeneration())
                        awaitCachedSegment(store, holder, request)
                    }
                }
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
                println("pump[$videoItag] elapsedMs=$elapsedMs segments=${segments?.size ?: -1}")
                println("pump[$videoItag] trace=${holder.session.diagnosticTrace}")
                segments.orEmpty().forEach {
                    println(
                        "pump[$videoItag] itag=${it.itag} seq=${it.sequence} startMs=${it.startMs} " +
                            "durationMs=${it.durationMs} bytes=${it.length}"
                    )
                }
                assertTrue(
                    segments.orEmpty().any { it.covers(video, playerTimeMs) },
                    "video[$videoItag] pump bytes",
                )
                assertTrue(
                    segments.orEmpty().any { it.covers(audio, playerTimeMs) },
                    "audio[$videoItag] pump bytes",
                )
            }
        } finally {
            store.release()
        }
    }

    private suspend fun awaitCachedSegment(
        store: SabrSessionStore,
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
    ): CachedSabrSegment {
        var segment = store.cachedSegment(holder, request)
        while (segment == null) {
            delay(50L)
            segment = store.cachedSegment(holder, request)
        }
        return segment
    }

    private suspend fun assertInitData(
        store: SabrSessionStore,
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
    ): Unit {
        val data = store.fetchInitializationData(holder, format)
        println("init itag=${format.itag} bytes=${data?.size ?: -1}")
        if (data == null || data.isEmpty()) println("init trace=${holder.session.diagnosticTrace}")
        assertTrue(data?.isNotEmpty() == true, "init bytes for itag ${format.itag}")
    }

    private fun requireAudioFormat(
        formats: List<YoutubeSabrFormat>,
        audioItag: Int,
    ): YoutubeSabrFormat =
        formats.filter { it.itag == audioItag && it.isAudio }
            .maxWithOrNull(compareBy<YoutubeSabrFormat> { it.isOriginalAudio }
                .thenBy { it.xtags.isNullOrBlank() }
                .thenBy { !it.isDrc }
                .thenBy { it.bitrate })
            ?: error("No SABR audio format for itag $audioItag")

    private fun extractSabrInfo(videoId: String): YoutubeSabrInfo {
        val service = NewPipe.getServiceByUrl("https://www.youtube.com/watch?v=$videoId")
        val linkHandler = service.streamLHFactory.fromUrl("https://www.youtube.com/watch?v=$videoId")
        val extractor = service.getStreamExtractor(linkHandler)
        extractor.fetchPage()
        val streamInfo = StreamInfo.getInfo(extractor)
        return sequence {
            streamInfo.videoStreams.forEach { yield(it.deliveryMethodInfo) }
            streamInfo.videoOnlyStreams.forEach { yield(it.deliveryMethodInfo) }
            streamInfo.audioStreams.forEach { yield(it.deliveryMethodInfo) }
        }.filterIsInstance<YoutubeSabrInfo>()
            .first { it.videoId == videoId }
    }

    private fun requireVideoFormat(
        formats: List<YoutubeSabrFormat>,
        videoItag: Int,
    ): YoutubeSabrFormat =
        formats.firstOrNull { it.itag == videoItag && it.isVideo }
            ?: error("No SABR video format for itag $videoItag")

    private fun CachedSabrSegment.covers(format: YoutubeSabrFormat, playerTimeMs: Long): Boolean {
        if (length <= 0 || init || itag != format.itag) return false
        val startMs = this.startMs
        val durationMs = this.durationMs
        return startMs >= 0 && durationMs > 0 &&
            playerTimeMs >= startMs && playerTimeMs < startMs + durationMs
    }
}
