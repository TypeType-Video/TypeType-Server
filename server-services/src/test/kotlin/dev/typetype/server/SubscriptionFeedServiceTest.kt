package dev.typetype.server

import dev.typetype.server.SubscriptionFeedTestFixtures.subscription
import dev.typetype.server.services.SubscriptionFeedPageResult
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionFeedServiceTest {
    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `snapshots and cursors remain scoped to their user`() = runTest {
        val subscriptions = SubscriptionsService()
        subscriptions.add("user-a", subscription("https://example.com/a", "A"))
        subscriptions.add("user-b", subscription("https://example.com/b", "B"))
        val service = SubscriptionFeedService(subscriptions, FakeChannelService(), FakeCacheService())

        assertTrue(service.getPage("user-a", 0, 30, null) is SubscriptionFeedPageResult.Preparing)
        assertTrue(service.getPage("user-b", 0, 30, null) is SubscriptionFeedPageResult.Preparing)
        service.awaitRefresh("user-a")
        service.awaitRefresh("user-b")

        val pageA = service.getPage("user-a", 0, 30, null) as SubscriptionFeedPageResult.Ready
        val pageB = service.getPage("user-b", 0, 30, null) as SubscriptionFeedPageResult.Ready
        assertEquals(listOf("https://example.com/a/video"), pageA.response.videos.map { it.url })
        assertEquals(listOf("https://example.com/b/video"), pageB.response.videos.map { it.url })
    }
}
