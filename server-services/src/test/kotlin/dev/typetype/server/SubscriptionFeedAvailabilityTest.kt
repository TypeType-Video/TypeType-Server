package dev.typetype.server

import dev.typetype.server.SubscriptionFeedTestFixtures.channel
import dev.typetype.server.SubscriptionFeedTestFixtures.subscription
import dev.typetype.server.SubscriptionFeedTestFixtures.video
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SubscriptionFeedAvailabilityTest {
    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @Test
    fun `aged snapshot stays available during refresh and survives refresh failure`() = runTest {
        TestDatabase.truncateAll()
        val subscriptions = SubscriptionsService()
        subscriptions.add(TEST_USER_ID, subscription("https://yt.com/c/a", "A"))
        val channels = mockk<ChannelService>()
        val gate = CompletableDeferred<Unit>()
        var now = 1000L
        var failRefresh = false
        coEvery { channels.getChannel(any(), null) } coAnswers {
            if (failRefresh) {
                gate.await()
                error("source unavailable")
            }
            channel(video(1000L, "A"))
        }
        val feed = SubscriptionFeedService(subscriptions, channels, FakeCacheService(), clock = { now })
        feed.getAllWithAvailability(TEST_USER_ID)
        feed.awaitRefresh(TEST_USER_ID)
        failRefresh = true
        now += 61_000
        try {
            val refreshing = feed.getAllWithAvailability(TEST_USER_ID)
            assertTrue(refreshing.available)
            assertEquals(1, refreshing.videos.size)
        } finally {
            gate.complete(Unit)
            feed.awaitRefresh(TEST_USER_ID)
        }
        val failed = feed.getAllWithAvailability(TEST_USER_ID)
        feed.awaitRefresh(TEST_USER_ID)
        assertFalse(failed.available)
        assertEquals(1, failed.videos.size)
    }
}
