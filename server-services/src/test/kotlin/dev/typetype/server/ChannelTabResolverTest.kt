package dev.typetype.server

import dev.typetype.server.services.toBaseChannelUrl
import dev.typetype.server.services.toChannelTab
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs

class ChannelTabResolverTest {

    @Test
    fun `channel streams url maps to livestreams tab`() {
        val url = "https://www.youtube.com/@test/streams?view=2"

        assertEquals(ChannelTabs.LIVESTREAMS, url.toChannelTab(null))
        assertEquals("https://www.youtube.com/@test", url.toBaseChannelUrl(ChannelTabs.LIVESTREAMS))
    }

    @Test
    fun `channel sort maps base url to videos tab`() {
        val url = "https://www.youtube.com/@test"

        assertEquals(ChannelTabs.VIDEOS, url.toChannelTab("latest"))
        assertEquals("https://www.youtube.com/@test", url.toBaseChannelUrl(ChannelTabs.VIDEOS))
    }
}
