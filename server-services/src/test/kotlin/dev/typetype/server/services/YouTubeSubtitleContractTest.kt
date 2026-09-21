package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class YouTubeSubtitleContractTest {
    @Test
    fun `legacy timed text selection preserves track and translation`() {
        val selection = subtitleSelectionFromTimedTextUrl(
            "https://www.youtube.com/api/timedtext?v=abcdefghijk&lang=en&kind=asr" +
                "&name=English&tlang=fr&expire=123&sig=secret",
        )

        requireNotNull(selection)
        assertEquals("abcdefghijk", selection.videoId)
        assertEquals("fr", selection.language)
        assertEquals("en", selection.sourceLanguage)
        assertEquals("fr", selection.translationLanguage)
        assertEquals("English", selection.trackName)
        assertEquals(YouTubeSubtitleVariant.Auto, selection.variant)
        assertEquals(YouTubeSubtitleFormat.Vtt, selection.format)
    }

    @Test
    fun `legacy selection rejects non YouTube and invalid video IDs`() {
        assertNull(
            subtitleSelectionFromTimedTextUrl(
                "https://example.com/api/timedtext?v=abcdefghijk&lang=en",
            ),
        )
        assertNull(
            subtitleSelectionFromTimedTextUrl(
                "https://www.youtube.com/api/timedtext?v=short&lang=en",
            ),
        )
    }
}
