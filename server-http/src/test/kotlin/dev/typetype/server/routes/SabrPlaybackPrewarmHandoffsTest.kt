package dev.typetype.server.routes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SabrPlaybackPrewarmHandoffsTest {
    @Test
    fun `diagnoseMiss explains itag track and startTime mismatches`() {
        val handoffs = SabrPlaybackPrewarmHandoffs()
        val storedKey = SabrPlaybackPrewarmKey(
            userId = "user-1",
            videoId = "vid-1",
            videoItag = 137,
            audioItag = 251,
            audioTrackId = "en",
            startTimeMs = 0L,
            audioOnly = false,
            isLive = false,
        )
        handoffs.remember(storedKey, SabrPlaybackPrewarmSession("tok-1", 0L, ready = true))

        assertEquals(
            "itag_mismatch(prewarm=137/251,req=248/251)",
            handoffs.diagnoseMiss(storedKey.copy(videoItag = 248)),
        )
        assertEquals(
            "track_mismatch(prewarm=en,req=ja)",
            handoffs.diagnoseMiss(storedKey.copy(audioTrackId = "ja")),
        )
        assertEquals(
            "start_mismatch(prewarm=0,req=15000)",
            handoffs.diagnoseMiss(storedKey.copy(startTimeMs = 15_000L)),
        )
        val otherUserKey = storedKey.copy(userId = "user-2")
        handoffs.remember(otherUserKey, SabrPlaybackPrewarmSession("tok-2", 0L, ready = true))
        assertEquals(
            "itag_mismatch(prewarm=137/251,req=248/251)",
            handoffs.diagnoseMiss(storedKey.copy(videoItag = 248)),
        )
        assertNotNull(handoffs.take(otherUserKey))

        assertEquals("no_entry", handoffs.diagnoseMiss(storedKey.copy(videoId = "other-vid")))

        assertNotNull(handoffs.take(storedKey))
        assertNull(handoffs.take(storedKey))
    }
}
