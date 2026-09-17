package dev.typetype.server

import dev.typetype.server.services.toChannelTab
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs

class ChannelTabCapabilityTest {
    @Test
    fun `services without channel tab support fall back to channel info`() {
        val tab = ServiceList.BiliBili.toChannelTab(
            "https://space.bilibili.com/946974",
            "latest",
        )

        assertNull(tab)
    }

    @Test
    fun `services with channel tab support keep sorted videos`() {
        val tab = ServiceList.YouTube.toChannelTab(
            "https://www.youtube.com/@test",
            "latest",
        )

        assertEquals(ChannelTabs.VIDEOS, tab)
    }
}
