package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs

class PlaylistListTabTest {
    @Test
    fun `lists courses only for a courses tab URL`() {
        assertEquals(ChannelTabs.COURSES, "https://www.youtube.com/@name/courses".playlistListTab())
        assertEquals(ChannelTabs.COURSES, "https://www.youtube.com/@name/courses/?x=1".playlistListTab())
        assertEquals(ChannelTabs.PLAYLISTS, "https://www.youtube.com/@name".playlistListTab())
        assertEquals(ChannelTabs.PLAYLISTS, "https://www.youtube.com/@name/playlists".playlistListTab())
        assertEquals(ChannelTabs.PLAYLISTS, "https://www.youtube.com/@courses".playlistListTab())
    }

    @Test
    fun `strips the courses tab back to the channel URL`() {
        assertEquals("https://www.youtube.com/@name", "https://www.youtube.com/@name/courses".toBaseChannelUrl(ChannelTabs.COURSES))
    }
}
