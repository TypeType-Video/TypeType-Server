package dev.typetype.server

import dev.typetype.server.models.RssFeedItem
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.RssVideoMetadata
import dev.typetype.server.services.RssVideoTypeFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RssVideoTypeFilterTest {
    @Test
    fun `separates regular shorts live and upcoming content`() {
        assertTrue(RssVideoTypeFilter.includes(feed(includeVideos = true), video(), NOW))
        assertFalse(RssVideoTypeFilter.includes(feed(includeVideos = false), video(), NOW))
        assertTrue(RssVideoTypeFilter.includes(feed(includeShorts = true), video(short = true), NOW))
        assertTrue(RssVideoTypeFilter.includes(feed(includeLive = true), video(live = true), NOW))
        assertTrue(
            RssVideoTypeFilter.includes(
                feed(includeUpcoming = true),
                video(duration = -1, publishedAt = NOW + 60_000),
                NOW,
            ),
        )
    }

    @Test
    fun `does not treat past unknown-duration or post-live videos as upcoming`() {
        val upcomingOnly = feed(includeUpcoming = true)
        assertFalse(
            RssVideoTypeFilter.includes(
                upcomingOnly,
                video(duration = -1, publishedAt = NOW - 60_000),
                NOW,
            ),
        )
        assertFalse(
            RssVideoTypeFilter.includes(
                upcomingOnly,
                video(duration = -1, publishedAt = NOW + 60_000, postLive = true),
                NOW,
            ),
        )
    }

    @Test
    fun `live and upcoming state takes priority over short form`() {
        val liveShort = video(short = true, live = true)
        assertTrue(RssVideoTypeFilter.includes(feed(includeLive = true), liveShort, NOW))
        assertFalse(RssVideoTypeFilter.includes(feed(includeShorts = true), liveShort, NOW))

        val upcomingShort = video(short = true, duration = -1, publishedAt = NOW + 60_000)
        assertTrue(RssVideoTypeFilter.includes(feed(includeUpcoming = true), upcomingShort, NOW))
        assertFalse(RssVideoTypeFilter.includes(feed(includeShorts = true), upcomingShort, NOW))
    }

    @Test
    fun `detects supported provider from canonical and short hosts`() {
        assertEquals(0, RssVideoMetadata.serviceId(video(url = "https://www.youtube.com/watch?v=video")))
        assertEquals(5, RssVideoMetadata.serviceId(video(url = "https://b23.tv/video")))
        assertEquals(5, RssVideoMetadata.serviceId(video(url = "https://www.bilibili.com/video/BV1")))
        assertEquals(6, RssVideoMetadata.serviceId(video(url = "https://nico.ms/sm1")))
        assertEquals(6, RssVideoMetadata.serviceId(video(url = "https://www.nicovideo.jp/watch/sm1")))
    }

    private fun feed(
        includeVideos: Boolean = false,
        includeShorts: Boolean = false,
        includeLive: Boolean = false,
        includeUpcoming: Boolean = false,
    ) = RssFeedItem(
        id = "feed",
        name = "Feed",
        scope = "all",
        channelUrls = emptyList(),
        serviceIds = listOf(0),
        includeVideos = includeVideos,
        includeShorts = includeShorts,
        includeLive = includeLive,
        includeUpcoming = includeUpcoming,
        enabled = true,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun video(
        duration: Long = 120,
        publishedAt: Long = NOW - 60_000,
        short: Boolean = false,
        live: Boolean = false,
        postLive: Boolean = false,
        url: String = "https://youtube.com/watch?v=video",
    ) = VideoItem(
        id = "video",
        title = "Video",
        url = url,
        thumbnailUrl = "",
        uploaderName = "Channel",
        uploaderUrl = "https://youtube.com/@channel",
        uploaderAvatarUrl = "",
        duration = duration,
        viewCount = 0,
        uploadDate = "",
        streamType = "video_stream",
        isShortFormContent = short,
        uploaderVerified = false,
        shortDescription = null,
        publishedAt = publishedAt,
        isLive = live,
        isPostLive = postLive,
        isLiveContent = live || postLive,
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
