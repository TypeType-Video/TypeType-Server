package dev.typetype.server

import dev.typetype.server.services.streamLiveMetadata
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.stream.StreamType

class LiveStreamTypesTest {

    @Test
    fun `live stream with manifest has live manifest`() {
        val metadata = streamLiveMetadata(StreamType.LIVE_STREAM, "https://example.com/live.m3u8", "")

        assertTrue(metadata.isLive)
        assertFalse(metadata.isPostLive)
        assertTrue(metadata.isLiveContent)
        assertTrue(metadata.hasLiveManifest)
    }

    @Test
    fun `post live stream is live content without live manifest flag`() {
        val metadata = streamLiveMetadata(StreamType.POST_LIVE_STREAM, "https://example.com/replay.m3u8", "")

        assertFalse(metadata.isLive)
        assertTrue(metadata.isPostLive)
        assertTrue(metadata.isLiveContent)
        assertFalse(metadata.hasLiveManifest)
    }
}
