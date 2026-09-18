package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.models.RssFeedRequest
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.RssFeedException
import dev.typetype.server.services.RssFeedManagementService
import dev.typetype.server.services.SubscriptionsService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class RssFeedManagementServiceTest {
    private val settings = AdminSettingsService()
    private val subscriptions = SubscriptionsService()
    private val service = RssFeedManagementService(settings, subscriptions)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
    }

    @Test
    fun `selected channels must be owned subscriptions and feeds stay isolated`() = runTest {
        enableRss()
        insertUser("user-a")
        insertUser("user-b")
        subscriptions.add("user-a", SubscriptionFeedTestFixtures.subscription("https://youtube.com/@a", "A"))
        subscriptions.add("user-b", SubscriptionFeedTestFixtures.subscription("https://youtube.com/@b", "B"))

        val created = service.create(
            "user-a",
            RssFeedRequest(name = "A only", scope = "channels", channelUrls = listOf("https://youtube.com/@a/")),
        )

        assertTrue(created.feedUrl.startsWith("https://video.example/api/rss/feeds/"))
        assertEquals(listOf("https://youtube.com/@a"), created.feed.channelUrls)
        assertEquals(listOf(created.feed), service.list("user-a"))
        assertTrue(service.list("user-b").isEmpty())
        val error = runCatching {
            service.create(
                "user-b",
                RssFeedRequest(name = "Not mine", scope = "channels", channelUrls = listOf("https://youtube.com/@a")),
            )
        }.exceptionOrNull() as RssFeedException
        assertEquals("rss_channel_not_subscribed", error.code)
    }

    @Test
    fun `accounts cannot mutate feeds they do not own`() = runTest {
        enableRss()
        insertUser("user-a")
        insertUser("user-b")
        val created = service.create("user-a", RssFeedRequest(name = "Private feed"))

        val attempts = listOf<suspend () -> Unit>(
            { service.update("user-b", created.feed.id, RssFeedRequest(name = "Changed")) },
            { service.setEnabled("user-b", created.feed.id, false) },
            { service.regenerate("user-b", created.feed.id) },
            { service.delete("user-b", created.feed.id) },
        )

        attempts.forEach { attempt ->
            val error = runCatching { attempt() }.exceptionOrNull() as RssFeedException
            assertEquals("rss_feed_not_found", error.code)
        }
        assertEquals(listOf(created.feed), service.list("user-a"))
    }

    @Test
    fun `disabled account retains feeds but cannot manage them`() = runTest {
        enableRss()
        insertUser("user-a")
        service.create("user-a", RssFeedRequest(name = "All"))
        service.adminSetUserEnabled("user-a", false)

        val error = runCatching { service.list("user-a") }.exceptionOrNull() as RssFeedException
        assertEquals("rss_user_disabled", error.code)
        assertEquals(1L, service.adminList(1, 20).total)
    }

    @Test
    fun `global disable retains feed configuration`() = runTest {
        enableRss()
        insertUser("user-a")
        val created = service.create("user-a", RssFeedRequest(name = "All"))
        settings.upsert(AdminSettingsItem(rssEnabled = false, rssPublicBaseUrl = "https://video.example"))

        val error = runCatching { service.list("user-a") }.exceptionOrNull() as RssFeedException
        assertEquals("rss_disabled", error.code)
        assertEquals(created.feed, service.adminList(1, 20).items.single().feed)

        enableRss()
        assertEquals(listOf(created.feed), service.list("user-a"))
    }

    @Test
    fun `admin cannot create an RSS policy for an unknown account`() = runTest {
        val error = runCatching { service.adminSetUserEnabled("missing", false) }
            .exceptionOrNull() as RssFeedException

        assertEquals("rss_user_not_found", error.code)
    }

    @Test
    fun `concurrent creation cannot exceed the account feed limit`() = runTest {
        settings.upsert(
            AdminSettingsItem(
                rssEnabled = true,
                rssPublicBaseUrl = "https://video.example",
                rssMaxFeedsPerUser = 1,
            ),
        )
        insertUser("user-a")

        val attempts = List(4) { index ->
            async { runCatching { service.create("user-a", RssFeedRequest(name = "Feed $index")) } }
        }.awaitAll()

        assertEquals(1, attempts.count(Result<*>::isSuccess))
        assertTrue(attempts.filter(Result<*>::isFailure).all {
            (it.exceptionOrNull() as RssFeedException).code == "rss_feed_limit_reached"
        })
    }

    private suspend fun enableRss() {
        settings.upsert(AdminSettingsItem(rssEnabled = true, rssPublicBaseUrl = "https://video.example"))
    }

    private fun insertUser(id: String) = transaction {
        UsersTable.insert {
            it[UsersTable.id] = id
            it[email] = "$id@test.local"
            it[passwordHash] = "hash"
            it[name] = id
            it[role] = "user"
            it[createdAt] = 1L
            it[updatedAt] = 1L
        }
    }
}
