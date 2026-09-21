package dev.typetype.server

import dev.typetype.server.routes.AudioOnlyByteRange
import dev.typetype.server.routes.parseAudioOnlyByteRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AudioOnlyByteRangeTest {
    @Test
    fun `parses explicit byte range`() {
        val range = parseAudioOnlyByteRange("bytes=100-199", 500)

        assertEquals(AudioOnlyByteRange.Satisfiable(100, 199, 500), range)
    }

    @Test
    fun `parses open byte range`() {
        val range = parseAudioOnlyByteRange("bytes=100-", 500)

        assertEquals(AudioOnlyByteRange.Satisfiable(100, 499, 500), range)
    }

    @Test
    fun `parses suffix byte range`() {
        val range = parseAudioOnlyByteRange("bytes=-100", 500)

        assertEquals(AudioOnlyByteRange.Satisfiable(400, 499, 500), range)
    }

    @Test
    fun `returns unsatisfiable when range starts after total`() {
        val range = parseAudioOnlyByteRange("bytes=500-600", 500)

        assertEquals(AudioOnlyByteRange.Unsatisfiable(500), range)
    }

    @Test
    fun `ignores malformed ranges`() {
        assertNull(parseAudioOnlyByteRange("items=0-1", 500))
        assertNull(parseAudioOnlyByteRange("bytes=0-1,2-3", 500))
    }
}
