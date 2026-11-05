package dev.typetype.server

import dev.typetype.server.services.HlsWarmupPlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HlsWarmupPlanTest {
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
        assertEquals(listOf(0L, 6_000L), media.segments.map { it.startMs })
        assertEquals("m1_SEGMENTTWOSEGMENTTWOSEGM", media.segments[1].handle)
    }
}
