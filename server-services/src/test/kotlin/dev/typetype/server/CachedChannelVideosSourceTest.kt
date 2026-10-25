package dev.typetype.server

import dev.typetype.server.SubscriptionFeedTestFixtures.channel
import dev.typetype.server.SubscriptionFeedTestFixtures.subscription
import dev.typetype.server.SubscriptionFeedTestFixtures.video
import dev.typetype.server.models.ChannelPlaylistsResponse
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.CachedChannelVideosSource
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.ChannelVideosSource
import dev.typetype.server.services.PublicCacheKey
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.Collections

class CachedChannelVideosSourceTest {
    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    private val pageRequests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val tabRequests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val pages = object : ChannelService {
        override suspend fun getChannel(url: String, nextpage: String?, sort: String?): ExtractionResult<ChannelResponse> {
            pageRequests += url
            return channel(video(1_000L, "A"))
        }

        override suspend fun getPlaylists(url: String, nextpage: String?): ExtractionResult<ChannelPlaylistsResponse> =
            ExtractionResult.Success(ChannelPlaylistsResponse(emptyList(), null))
    }
    private val tabs = ChannelVideosSource { url ->
        tabRequests += url
        channel(video(2_000L, "A", live = true))
    }

    @Test
    fun `live tabs skip the channel page and stay cached for ten minutes`() = runTest {
        val cache = ExpiringFakeCache()
        val source = CachedChannelVideosSource(pages, tabs, cache)
        val url = "https://www.youtube.com/channel/UCa/streams"

        source.firstPage(url)
        source.firstPage(url)

        assertEquals(listOf(url), tabRequests)
        assertTrue(pageRequests.isEmpty())
        assertEquals(600L, cache.ttls[PublicCacheKey.of("channel-videos", url)])
    }

    @Test
    fun `channel home pages reuse the full channel page cache`() = runTest {
        val source = CachedChannelVideosSource(pages, tabs, ExpiringFakeCache())

        source.firstPage("https://www.youtube.com/channel/UCa")

        assertEquals(listOf("https://www.youtube.com/channel/UCa"), pageRequests)
        assertTrue(tabRequests.isEmpty())
    }

    @Test
    fun `feed refresh asks the videos source for every tab`() = runTest {
        TestDatabase.truncateAll()
        val subscriptions = SubscriptionsService()
        subscriptions.add(TEST_USER_ID, subscription("https://www.youtube.com/channel/UCa", "A"))
        val feed = SubscriptionFeedService(
            subscriptions,
            pages,
            FakeCacheService(),
            channelVideos = CachedChannelVideosSource(pages, tabs, ExpiringFakeCache()),
        )

        feed.getAllWithAvailability(TEST_USER_ID)
        feed.awaitRefresh(TEST_USER_ID)

        assertEquals(listOf("https://www.youtube.com/channel/UCa/streams"), tabRequests)
        assertEquals(listOf("https://www.youtube.com/channel/UCa"), pageRequests)
    }
}
