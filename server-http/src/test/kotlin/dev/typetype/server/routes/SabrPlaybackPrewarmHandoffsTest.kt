package dev.typetype.server.routes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SabrPlaybackPrewarmHandoffsTest {
    @Test
    fun `takeOrDiagnose explains itag track and startTime mismatches`() {
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
            handoffs.takeOrDiagnose(storedKey.copy(videoItag = 248)).second,
        )
        assertEquals(
            "track_mismatch(prewarm=en,req=ja)",
            handoffs.takeOrDiagnose(storedKey.copy(audioTrackId = "ja")).second,
        )
        assertEquals(
            "start_mismatch(prewarm=0,req=15000)",
            handoffs.takeOrDiagnose(storedKey.copy(startTimeMs = 15_000L)).second,
        )
        val otherUserKey = storedKey.copy(userId = "user-2")
        handoffs.remember(otherUserKey, SabrPlaybackPrewarmSession("tok-2", 0L, ready = true))
        assertEquals(
            "itag_mismatch(prewarm=137/251,req=248/251)",
            handoffs.takeOrDiagnose(storedKey.copy(videoItag = 248)).second,
        )
        assertNotNull(handoffs.takeOrDiagnose(otherUserKey).first)

        assertEquals("no_entry", handoffs.takeOrDiagnose(storedKey.copy(videoId = "other-vid")).second)

        assertNotNull(handoffs.takeOrDiagnose(storedKey).first)
        assertEquals(null to "no_entry", handoffs.takeOrDiagnose(storedKey))
    }

    @Test
    fun `takeOrDiagnose reports expired entries before evicting them`() {
        val handoffs = SabrPlaybackPrewarmHandoffs(ttlNanos = -1L)
        val key = SabrPlaybackPrewarmKey(
            userId = "user-1",
            videoId = "vid-1",
            videoItag = 137,
            audioItag = 251,
            audioTrackId = "en",
            startTimeMs = 0L,
            audioOnly = false,
            isLive = false,
        )
        handoffs.remember(key, SabrPlaybackPrewarmSession("tok-expired", 0L, ready = true))
        assertEquals(null to "expired", handoffs.takeOrDiagnose(key))
        assertEquals(null to "no_entry", handoffs.takeOrDiagnose(key))
    }
}
