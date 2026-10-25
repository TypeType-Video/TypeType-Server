package dev.typetype.server

import dev.typetype.server.SubscriptionFeedTestFixtures.channel
import dev.typetype.server.SubscriptionFeedTestFixtures.subscription
import dev.typetype.server.SubscriptionFeedTestFixtures.video
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.ChannelVideosSource
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class SubscriptionFeedCarryOverTest {
    companion object {
        const val A = "https://yt.com/c/A"
        const val B = "https://yt.com/c/B"
        const val YOUTUBE = "https://www.youtube.com/channel/UCy"

        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    private val pages = ConcurrentHashMap<String, ExtractionResult<ChannelResponse>>()
    private val subscriptions = SubscriptionsService()
    private lateinit var feed: SubscriptionFeedService

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        feed = SubscriptionFeedService(
            subscriptions,
            FakeChannelService(),
            FakeCacheService(),
            channelVideos = ChannelVideosSource { url -> pages.getValue(url) },
        )
    }

    @Test
    fun `a failing channel keeps its videos from the previous snapshot`() = runTest {
        subscriptions.add(TEST_USER_ID, subscription(A, "A"))
        subscriptions.add(TEST_USER_ID, subscription(B, "B"))
        pages[A] = channel(video(1_000L, "A", url = "a/1"))
        pages[B] = channel(video(2_000L, "B", url = "b/1"))
        refresh()

        pages[A] = channel(video(3_000L, "A", url = "a/2"))
        pages[B] = ExtractionResult.Failure("blocked")
        refresh()

        assertEquals(listOf("a/2", "b/1"), feedUrls())
    }

    @Test
    fun `an empty channel page keeps its videos from the previous snapshot`() = runTest {
        subscriptions.add(TEST_USER_ID, subscription(A, "A"))
        subscriptions.add(TEST_USER_ID, subscription(B, "B"))
        pages[A] = channel(video(1_000L, "A", url = "a/1"))
        pages[B] = channel(video(2_000L, "B", url = "b/1"))
        refresh()

        pages[B] = channel()
        refresh()

        assertEquals(listOf("b/1", "a/1"), feedUrls())
    }

    @Test
    fun `a failing live tab keeps the previous live videos only`() = runTest {
        subscriptions.add(TEST_USER_ID, subscription(YOUTUBE, "Y"))
        pages[YOUTUBE] = channel(video(1_000L, "Y", url = "y/vod"))
        pages["$YOUTUBE/streams"] = channel(video(2_000L, "Y", url = "y/live", live = true))
        refresh()

        pages[YOUTUBE] = channel(video(3_000L, "Y", url = "y/new"))
        pages["$YOUTUBE/streams"] = ExtractionResult.Failure("blocked")
        refresh()

        assertEquals(setOf("y/new", "y/live"), feedUrls().toSet())
    }

    private suspend fun refresh() {
        feed.invalidate(TEST_USER_ID)
        feed.awaitRefresh(TEST_USER_ID)
    }

    private suspend fun feedUrls(): List<String> = feed.getCachedAll(TEST_USER_ID).orEmpty().map { it.url }
}
