package dev.typetype.server

import dev.typetype.server.SubscriptionFeedTestFixtures.channel
import dev.typetype.server.SubscriptionFeedTestFixtures.subscription
import dev.typetype.server.SubscriptionFeedTestFixtures.video
import dev.typetype.server.models.ChannelPlaylistsResponse
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.ChannelNotificationPreferenceService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.NotificationsService
import dev.typetype.server.services.SubscriptionFeedPageResult
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class SubscriptionFeedRefreshTimingTest {
    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    private val channels = CountingChannelService()
    private val subscriptions = SubscriptionsService()
    private var now = 1_000_000L
    private lateinit var feed: SubscriptionFeedService

    @BeforeEach
    fun clean() = runTest {
        TestDatabase.truncateAll()
        subscriptions.add(TEST_USER_ID, subscription("https://yt.com/c/a", "A"))
        feed = SubscriptionFeedService(subscriptions, channels, FakeCacheService(), clock = { now })
    }

    @Test
    fun `unread count reads inside the freshness window start no refresh`() = runTest {
        val notifications = NotificationsService(feed, ChannelNotificationPreferenceService(subscriptions))
        notifications.getUnreadCount(TEST_USER_ID)
        feed.awaitRefresh(TEST_USER_ID)
        val coldFetches = channels.calls.get()

        repeat(10) {
            now += 60_000L
            notifications.getUnreadCount(TEST_USER_ID)
            feed.awaitRefresh(TEST_USER_ID)
        }

        assertEquals(1, coldFetches)
        assertEquals(coldFetches, channels.calls.get())
    }

    @Test
    fun `timer refreshes a recently active user once per interval`() = runTest {
        feed.getAllWithAvailability(TEST_USER_ID)
        feed.awaitRefresh(TEST_USER_ID)

        now += 11 * 60_000L
        feed.getAllWithAvailability(TEST_USER_ID)
        feed.refreshDueUsers()
        feed.awaitRefresh(TEST_USER_ID)
        assertEquals(1, channels.calls.get())

        now += 5 * 60_000L
        feed.refreshDueUsers()
        feed.awaitRefresh(TEST_USER_ID)
        feed.refreshDueUsers()
        feed.awaitRefresh(TEST_USER_ID)
        assertEquals(2, channels.calls.get())
    }

    @Test
    fun `timer leaves users who stopped reading alone`() = runTest {
        feed.getAllWithAvailability(TEST_USER_ID)
        feed.awaitRefresh(TEST_USER_ID)

        now += 2 * 60 * 60_000L
        feed.refreshDueUsers()
        feed.awaitRefresh(TEST_USER_ID)

        assertEquals(1, channels.calls.get())
    }

    @Test
    fun `cold reads retry a failed first refresh at most once a minute`() = runTest {
        channels.fail = true
        assertTrue(feed.getPage(TEST_USER_ID, 0, 30, null) is SubscriptionFeedPageResult.Preparing)
        feed.awaitRefresh(TEST_USER_ID)
        repeat(5) {
            feed.getPage(TEST_USER_ID, 0, 30, null)
            feed.awaitRefresh(TEST_USER_ID)
        }
        assertEquals(1, channels.calls.get())

        now += 61_000L
        feed.getPage(TEST_USER_ID, 0, 30, null)
        feed.awaitRefresh(TEST_USER_ID)
        assertEquals(2, channels.calls.get())
    }

    private class CountingChannelService : ChannelService {
        val calls = AtomicInteger()
        var fail = false

        override suspend fun getChannel(url: String, nextpage: String?, sort: String?): ExtractionResult<ChannelResponse> {
            calls.incrementAndGet()
            return if (fail) ExtractionResult.Failure("blocked") else channel(video(1_000L, "A"))
        }

        override suspend fun getPlaylists(url: String, nextpage: String?): ExtractionResult<ChannelPlaylistsResponse> =
            ExtractionResult.Success(ChannelPlaylistsResponse(emptyList(), null))
    }
}
