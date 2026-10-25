package dev.typetype.server

import dev.typetype.server.SubscriptionFeedTestFixtures.channel
import dev.typetype.server.SubscriptionFeedTestFixtures.subscription
import dev.typetype.server.SubscriptionFeedTestFixtures.video
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.ChannelVideosSource
import dev.typetype.server.services.SubscriptionFeedCacheKeys
import dev.typetype.server.services.SubscriptionShortsBlendService
import dev.typetype.server.services.SubscriptionShortsFeedService
import dev.typetype.server.services.SubscriptionsService
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections

class SubscriptionShortsFeedCachingTest {
    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    private val subscriptions = SubscriptionsService()
    private val cache = ExpiringFakeCache()
    private val requested: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var failing = false
    private val source = ChannelVideosSource { url ->
        requested += url
        if (failing) ExtractionResult.Failure("blocked") else channel(video(1_000L, "A", short = true))
    }
    private val feed = SubscriptionShortsFeedService(subscriptions, FakeChannelService(), mockk<SubscriptionShortsBlendService>(), cache, source)

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `a shorts feed whose channels all failed is kept for thirty seconds`() = runTest {
        subscriptions.add(TEST_USER_ID, subscription("https://www.youtube.com/@a", "A"))
        failing = true

        feed.getFeed(TEST_USER_ID, 0, 10)

        assertEquals(30L, cache.ttls[SubscriptionFeedCacheKeys.shorts(TEST_USER_ID)])
    }

    @Test
    fun `a complete shorts feed is kept for an hour`() = runTest {
        subscriptions.add(TEST_USER_ID, subscription("https://www.youtube.com/@a", "A"))

        feed.getFeed(TEST_USER_ID, 0, 10)

        assertEquals(3_600L, cache.ttls[SubscriptionFeedCacheKeys.shorts(TEST_USER_ID)])
    }

    @Test
    fun `providers without a shorts tab are asked for their channel page only`() = runTest {
        subscriptions.add(TEST_USER_ID, subscription("https://space.bilibili.com/42", "B"))

        feed.getFeed(TEST_USER_ID, 0, 10)

        assertEquals(listOf("https://space.bilibili.com/42"), requested)
    }
}
