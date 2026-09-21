package dev.typetype.server.services

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import dev.typetype.server.sabr.SabrSegmentRequest

@EnabledIfSystemProperty(named = "sabr.probe", matches = "true")
@Tag("network")
class SabrSessionStoreTest {

    private val tokenServiceUrl: String =
        sabrProbeTokenServiceUrl()

    @Test
    fun storeRoundTrip(): Unit = runBlocking {
        NewPipeInitializer.init()
        val store = SabrSessionStore(tokenServiceUrl = tokenServiceUrl)
        val videoId = sabrProbeVideoId()
        val playerTimeMs = sabrProbePlayerTimeMs()
        val timeoutMs = sabrProbeTimeoutMs()
        val audioItag = sabrProbeAudioItag()
        val videoItag = sabrProbeVideoItags().first()
        val userId = "sabr-probe-user"

        println(
            "\n========== SABR store round-trip: $videoId " +
                "playerTimeMs=$playerTimeMs timeoutMs=$timeoutMs =========="
        )
        val prepared = store.fetchInfo(videoId) ?: error("SABR probe failed")
        val info = prepared.info
        val audio = info.formats.firstOrNull { it.itag == audioItag && it.isAudio }
            ?: info.findBestAudioFormat()
        val video = info.formats.firstOrNull { it.itag == videoItag && it.isVideo }
            ?: info.findLowestVideoFormat()
        println("probe picked: audio=${audio?.itag} video=${video?.itag}")
        check(audio != null && video != null)
        printSabrProbeFormat("audio", audio)
        printSabrProbeFormat("video", video)

        val holder = store.getOrCreate(videoId, userId, info, audio, video, prepared.initialToken)
        store.ensureWarmed(holder)
        holder.setActiveTracks(videoActive = true, audioActive = true)
        holder.setPlayerTimeMs(playerTimeMs)
        println("session complete (cold): ${holder.session.isComplete}")

        val requests = listOf(
            SabrSegmentRequest.initialization(video),
            SabrSegmentRequest.initialization(audio),
        ) + mediaRequestsForProbe(holder, video, audio, playerTimeMs)
        for ((i, req) in requests.withIndex()) {
            val result = fetchSabrProbeSegment(store, holder, req, timeoutMs)
            printSabrProbeFetch("req[$i]", holder, req, result)
        }
        println("session complete after fetches: ${holder.session.isComplete}")

        val looked = store.lookup(videoId, userId, audio.itag, video.itag)
        println("lookup same key: ${looked === holder}")

        store.release()
    }

    private fun mediaRequestsForProbe(
        holder: SabrSessionHolder,
        video: dev.typetype.server.sabr.YoutubeSabrFormat,
        audio: dev.typetype.server.sabr.YoutubeSabrFormat,
        playerTimeMs: Long,
    ): List<SabrSegmentRequest> {
        val videoSequence = System.getenv("SABR_PROBE_VIDEO_SEQUENCE")?.toIntOrNull()
        val audioSequence = System.getenv("SABR_PROBE_AUDIO_SEQUENCE")?.toIntOrNull()
        if (videoSequence == null && audioSequence == null) return holder.mediaRequestsAt(playerTimeMs)
        return listOfNotNull(
            videoSequence?.let { SabrSegmentRequest.media(video, it) },
            audioSequence?.let { SabrSegmentRequest.media(audio, it) },
        )
    }
}
