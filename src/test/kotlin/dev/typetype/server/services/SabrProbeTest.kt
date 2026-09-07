package dev.typetype.server.services

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import dev.typetype.server.sabr.YoutubeSabrClientProfile
import dev.typetype.server.sabr.SabrAdapter
import dev.typetype.server.sabr.YoutubeSabrSession

@EnabledIfSystemProperty(named = "sabr.probe", matches = "true")
@Tag("network")
class SabrProbeTest {

    private val tokenServiceUrl: String =
        sabrProbeTokenServiceUrl()

    private val probeVideos: List<String> =
        sabrProbeVideoIds()

    @Test
    fun probeSabr(): Unit {
        NewPipeInitializer.init()
        val loc = Localization("en", "US")
        val country = ContentCountry("US")
        val tokenClient = TypetypeTokenSabrTokenClient(tokenServiceUrl)
        val profile = YoutubeSabrClientProfile.WEB
        val playerTimeMs = sabrProbePlayerTimeMs()
        val audioItag = sabrProbeAudioItag()
        val videoItags = sabrProbeVideoItags()
        println(
            "config videos=${probeVideos.joinToString(",")} playerTimeMs=$playerTimeMs " +
                "audioItag=$audioItag videoItags=${videoItags.joinToString(",")}"
        )

        for (videoId in probeVideos) {
            println("\n========== SABR probe: $videoId ==========")
            try {
                val token = tokenClient.fetch(videoId) ?: error("No SABR token")
                val info = TypetypeYoutubeSessionPoTokenProvider.withToken(token) {
                    SabrAdapter.fetchSabrInfo(videoId, profile, loc, country)
                }
                println("serverAbrStreamingUrl present: ${!info.serverAbrStreamingUrl.isNullOrEmpty()}")
                println("videoPlaybackUstreamerConfig present: ${!info.videoPlaybackUstreamerConfig.isNullOrEmpty()}")
                println("--- formats (itag | A/V | height×width | bitrate | mime | audioTrack | approxDurMs) ---")
                info.formats.forEach { f ->
                    val kind = when {
                        f.isAudio -> "A"
                        f.isVideo -> "V"
                        else -> "?"
                    }
                    println(
                        "  ${f.itag} | $kind | ${f.height}×${f.width} | br=${f.bitrate} | " +
                            "${f.mimeType} | track=${f.audioTrackId} | dur≈${f.approxDurationMs}ms"
                    )
                }
                val hasAvc = info.formats.any { it.itag == 137 }
                println("AVC itag 137 present: $hasAvc")
                for (videoItag in videoItags) {
                    val configuredVideo = info.formats.firstOrNull { it.itag == videoItag && it.isVideo }
                    println("configured video itag $videoItag present: ${configuredVideo != null}")
                    configuredVideo?.let { printSabrProbeFormat("configured video[$videoItag]", it) }
                }

                val audio = info.formats.firstOrNull { it.itag == audioItag && it.isAudio }
                    ?: info.findBestAudioFormat()
                val video = info.formats.firstOrNull { it.itag == videoItags.first() && it.isVideo }
                    ?: info.findLowestVideoFormat()
                println("selected pair: audio=${audio?.itag} video=${video?.itag}")
                if (audio == null || video == null) {
                    println("no usable audio/video pair — skipping session")
                    continue
                }

                val provider = TypetypeTokenSabrPoTokenProvider(tokenClient, token)
                val session = YoutubeSabrSession(info, audio, video, provider)
                session.streamState.setActiveTrackTypes(true, true)
                session.streamState.setPlayerTimeMs(playerTimeMs)
                println("--- pumpOnce #1 ---")
                val segments = session.pumpOnce(loc)
                println("segments returned: ${segments.size}")
                segments.take(8).forEach { s ->
                    println("  ${sabrProbeSegmentHeader(s)}")
                }
                println("session complete: ${session.isComplete}")
            } catch (e: Exception) {
                println("FAILED on $videoId: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
