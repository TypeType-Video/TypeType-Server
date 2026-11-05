package dev.typetype.server

import dev.typetype.server.services.HlsWarmupPlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HlsWarmupPlanTest {
    @Test
    fun `starts playback from the lowest variant and every audio rendition`() {
        val master = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="main",DEFAULT=YES,URI="../media/m1_AUDIOAUDIOAUDIOAUDIOAUDI"
            #EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1920x1080,AUDIO="audio"
            ../media/m1_HIGHHIGHHIGHHIGHHIGHHIGH
            #EXT-X-STREAM-INF:BANDWIDTH=600000,RESOLUTION=640x360,AUDIO="audio"
            ../media/m1_LOWLOWLOWLOWLOWLOWLOWLOW
        """.trimIndent()

        val plan = HlsWarmupPlan.fromMaster(master)

        assertEquals(
            listOf(
                HlsWarmupPlan.ChildPlaylist("m1_HIGHHIGHHIGHHIGHHIGHHIGH", startsPlayback = false),
                HlsWarmupPlan.ChildPlaylist("m1_LOWLOWLOWLOWLOWLOWLOWLOW", startsPlayback = true),
                HlsWarmupPlan.ChildPlaylist("m1_AUDIOAUDIOAUDIOAUDIOAUDI", startsPlayback = true),
            ),
            plan,
        )
    }

    @Test
    fun `finds the init section, the key and the first segment of a media playlist`() {
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="../media/m1_KEYKEYKEYKEYKEYKEYKEYKEY"
            #EXT-X-MAP:URI="../media/m1_INITINITINITINITINITINIT"
            #EXTINF:6.0,
            ../media/m1_SEGMENTONESEGMENTONESEGM
            #EXTINF:6.0,
            ../media/m1_SEGMENTTWOSEGMENTTWOSEGM
        """.trimIndent()

        val media = HlsWarmupPlan.fromMediaPlaylist(playlist)

        assertEquals(listOf("m1_KEYKEYKEYKEYKEYKEYKEYKEY", "m1_INITINITINITINITINITINIT"), media.headers)
        assertEquals("m1_SEGMENTONESEGMENTONESEGM", media.firstSegment)
    }
}
