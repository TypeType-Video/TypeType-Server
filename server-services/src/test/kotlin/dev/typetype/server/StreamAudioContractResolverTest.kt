package dev.typetype.server

import dev.typetype.server.services.StreamAudioContractResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamAudioContractResolverTest {
    @Test
    fun `original track id becomes preferred default`() {
        val fr = testAudioStream(audioTrackId = "fr.0", audioTrackName = "fr", audioLocale = "fr")
        val original = testAudioStream(
            audioTrackId = "en-US.4",
            audioTrackName = "en-US (original)",
            audioLocale = "en",
        )
        val response = testStreamResponse(audioStreams = listOf(fr, original))

        val resolved = StreamAudioContractResolver.apply(response, fallbackLanguage = "es")

        assertEquals("en-US.4", resolved.originalAudioTrackId)
        assertEquals("en-US.4", resolved.preferredDefaultAudioTrackId)
        assertFalse(resolved.audioStreams.first { it.audioTrackId == "fr.0" }.isOriginal)
        assertTrue(resolved.audioStreams.first { it.audioTrackId == "en-US.4" }.isOriginal)
    }

    @Test
    fun `fallback language wins when no original track exists`() {
        val fr = testAudioStream(audioTrackId = "fr.0", audioTrackName = "fr", audioLocale = "fr")
        val en = testAudioStream(audioTrackId = "en.0", audioTrackName = "en", audioLocale = "en")
        val response = testStreamResponse(audioStreams = listOf(fr, en))

        val resolved = StreamAudioContractResolver.apply(response, fallbackLanguage = "en")

        assertNull(resolved.originalAudioTrackId)
        assertEquals("en.0", resolved.preferredDefaultAudioTrackId)
        assertTrue(resolved.audioStreams.none { it.isOriginal })
    }

    @Test
    fun `first valid track id is used when original and fallback are unavailable`() {
        val unknown = testAudioStream(audioTrackId = null, audioTrackName = "und", audioLocale = null)
        val de = testAudioStream(audioTrackId = "de.0", audioTrackName = "de", audioLocale = "de")
        val response = testStreamResponse(audioStreams = listOf(unknown, de))

        val resolved = StreamAudioContractResolver.apply(response, fallbackLanguage = "en")

        assertNull(resolved.originalAudioTrackId)
        assertEquals("de.0", resolved.preferredDefaultAudioTrackId)
    }
}
