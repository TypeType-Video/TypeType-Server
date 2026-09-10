package dev.typetype.server

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.HomeRecommendationCursor
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TrendingService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeRecommendationServiceFastPathTest {
    private val cache: CacheService = mockk()
    private val channelService: ChannelService = mockk()
    private val trendingService: TrendingService = mockk()
    private val searchService: SearchService = mockk()
    private val subscriptions = SubscriptionsService()
    private val resolverDeps = homeResolverDependencies(
        subscriptions = subscriptions,
        channelService = channelService,
        cache = cache,
        trendingService = trendingService,
    )
    private val service = HomeRecommendationService(
        poolResolver = buildHomeResolver(resolverDeps),
    )

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        coEvery { cache.get(any()) } returns null
        coEvery { cache.set(any(), any(), any()) } returns Unit
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(emptyList())
        coEvery { searchService.search(any(), any(), any(), any(), any()) } returns ExtractionResult.Success(SearchPageResponse(emptyList(), null, null, false))
    }

    @Test
    fun `cold start fast path can return empty when no seeds are cached`() = runTest {
        val now = System.currentTimeMillis()
        subscriptions.add(TEST_USER_ID, SubscriptionItem("https://yt.com/c/a", "A", ""))
        coEvery { channelService.getChannel("https://yt.com/c/a", null) } coAnswers {
            delay(2_500)
            ExtractionResult.Success(ChannelResponse("A", "", "", "", 0L, false, listOf(video("s1", now)), null))
        }
        val response = service.getHome(TEST_USER_ID, 0, 4, HomeRecommendationCursor(), context)
        assertTrue(response.items.isEmpty())
    }

    @Test
    fun `cached subscription feed avoids channel fetch`() = runTest {
        val now = System.currentTimeMillis()
        subscriptions.add(TEST_USER_ID, SubscriptionItem("https://yt.com/c/a", "A", ""))
        val cachedFeed = CacheJson.encodeToString(
            ListSerializer(VideoItem.serializer()),
            listOf(video("s1", now), video("s2", now - 1)),
        )
        coEvery { cache.get(match { it.startsWith("recommendations:home:") }) } returns null
        coEvery { cache.get(match { it.startsWith("feed:") }) } returns cachedFeed
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(listOf(video("d1", now - 1)))
        coEvery { channelService.getChannel(any(), any()) } coAnswers {
            delay(2_500)
            ExtractionResult.Failure("slow channel fetch")
        }
        val response = service.getHome(TEST_USER_ID, 0, 4, HomeRecommendationCursor(), context)
        assertTrue(response.items.isNotEmpty())
    }

    private fun video(id: String, uploaded: Long, channel: String = "Channel"): VideoItem = VideoItem(
        id, id, "https://yt.com/v/$id", "", channel, "https://yt.com/c/$channel", "", 60, 0, "", uploaded,
        "video_stream", false, false, null,
    )

    private val context = defaultContext()
}
