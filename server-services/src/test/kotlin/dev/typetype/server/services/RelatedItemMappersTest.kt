package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class RelatedItemMappersTest {
    @Test
    fun `video item preserves membership requirement`() {
        val item = StreamInfoItem(
            0,
            "https://www.youtube.com/watch?v=member",
            "Members-only video",
            StreamType.VIDEO_STREAM,
        ).apply {
            setRequiresMembership(true)
        }

        assertTrue(item.toVideoItem().requiresMembership)
    }
}
