package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem

class BilibiliPlaylistItemMappersTest {
    @Test
    fun `public playlist mapper preserves Bilibili collection and series types`() {
        assertEquals("collection", playlistInfo(collectionUrl).toPublicPlaylistItem().playlistType)
        assertEquals("series", playlistInfo(seriesUrl).toPublicPlaylistItem().playlistType)
        assertEquals("normal", playlistInfo("https://www.youtube.com/playlist?list=PL123")
            .toPublicPlaylistItem().playlistType)
    }

    @Test
    fun `playlist result mapper preserves Bilibili collection and series types`() {
        assertEquals("collection", playlistInfoItem(collectionUrl).toPlaylistResultItem().playlistType)
        assertEquals("series", playlistInfoItem(seriesUrl).toPlaylistResultItem().playlistType)
        assertEquals("normal", playlistInfoItem("https://www.youtube.com/playlist?list=PL123")
            .toPlaylistResultItem().playlistType)
    }

    private fun playlistInfo(url: String): PlaylistInfo {
        val info = mockk<PlaylistInfo>()
        every { info.url } returns url
        every { info.name } returns "Playlist"
        every { info.thumbnailUrl } returns "https://example.com/thumbnail.jpg"
        every { info.uploaderName } returns "Channel"
        every { info.streamCount } returns 3L
        every { info.playlistType } returns PlaylistInfo.PlaylistType.NORMAL
        return info
    }

    private fun playlistInfoItem(url: String): PlaylistInfoItem = PlaylistInfoItem(3, url, "Playlist").apply {
        thumbnailUrl = "https://example.com/thumbnail.jpg"
        uploaderName = "Channel"
        streamCount = 3L
        playlistType = PlaylistInfo.PlaylistType.NORMAL
    }

    private companion object {
        const val collectionUrl =
            "https://www.bilibili.com/medialist/play/23630128?business=space_collection&business_id=2216020"
        const val seriesUrl =
            "https://www.bilibili.com/medialist/play/23630128?business=space_series&business_id=340933"
    }
}
