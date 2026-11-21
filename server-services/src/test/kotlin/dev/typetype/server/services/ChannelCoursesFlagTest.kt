package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.search.filter.FilterItem

class ChannelCoursesFlagTest {
    private val url = "https://www.youtube.com/@name"

    private fun tab(name: String) = ListLinkHandler(url, url, "id", listOf(FilterItem(0, name)), emptyList())

    private fun channel(vararg tabs: String) =
        ChannelInfo(0, "id", url, url, "Name", ListLinkHandler(url, url, "id", emptyList(), emptyList()))
            .apply {
                relatedItems = emptyList()
                setTabs(tabs.map(::tab))
            }

    @Test
    fun `flags a channel that lists a courses tab`() {
        assertTrue(channel(ChannelTabs.PLAYLISTS, ChannelTabs.COURSES).toChannelResponse().hasCourses)
        assertFalse(channel(ChannelTabs.PLAYLISTS).toChannelResponse().hasCourses)
    }

    @Test
    fun `keeps the flag on a sorted tab page built from the channel metadata`() {
        val videos = ChannelTabInfo(0, tab(ChannelTabs.VIDEOS)).apply { relatedItems = emptyList() }

        assertTrue(videos.toChannelTabResponse(channel(ChannelTabs.COURSES)).hasCourses)
        assertFalse(videos.toChannelTabResponse(channel(ChannelTabs.PLAYLISTS)).hasCourses)
        assertFalse(videos.toChannelTabResponse().hasCourses)
    }
}
