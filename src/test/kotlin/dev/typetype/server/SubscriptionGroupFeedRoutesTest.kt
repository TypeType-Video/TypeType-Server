package dev.typetype.server

import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.routes.subscriptionFeedRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionGroupMembershipResult
import dev.typetype.server.services.SubscriptionGroupWriteResult
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionsService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionGroupFeedRoutesTest {
    private val subscriptions = SubscriptionsService()
    private val groups = SubscriptionGroupsService()
    private lateinit var feed: SubscriptionFeedService
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        feed = SubscriptionFeedService(subscriptions, FakeChannelService(), FakeCacheService())
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { subscriptionFeedRoutes(feed, auth, groups) }
        }
        block()
    }

    @Test
    fun `group and ungrouped feeds project one shared global snapshot`() = withApp {
        subscriptions.add(TEST_USER_ID, subscription("one"))
        subscriptions.add(TEST_USER_ID, subscription("two"))
        val group = (groups.create(TEST_USER_ID, "Work") as SubscriptionGroupWriteResult.Success).group
        assertEquals(
            SubscriptionGroupMembershipResult.Success,
            groups.addSubscription(TEST_USER_ID, group.id, channel("one")),
        )

        assertEquals(HttpStatusCode.Accepted, requestFeed(groupId = group.id).status)
        feed.awaitRefresh(TEST_USER_ID)

        assertEquals(listOf("${channel("one")}/video"), requestReadyFeed(groupId = group.id).videos.map { it.url })
        assertEquals(listOf("${channel("two")}/video"), requestReadyFeed(ungrouped = true).videos.map { it.url })
        assertEquals(2, requestReadyFeed().videos.size)
    }

    @Test
    fun `cursor cannot be reused with another subscription filter`() = withApp {
        subscriptions.add(TEST_USER_ID, subscription("one"))
        subscriptions.add(TEST_USER_ID, subscription("two"))
        val group = (groups.create(TEST_USER_ID, "Work") as SubscriptionGroupWriteResult.Success).group
        groups.addSubscription(TEST_USER_ID, group.id, channel("one"))
        assertEquals(HttpStatusCode.Accepted, requestFeed(limit = 1).status)
        feed.awaitRefresh(TEST_USER_ID)
        val cursor = requireNotNull(requestReadyFeed(limit = 1).nextpage)

        val response = requestFeed(limit = 1, cursor = cursor, groupId = group.id)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("subscription_feed_invalid_cursor"))
    }

    @Test
    fun `group feed follows the fetched subscription source when uploader url differs`() = withApp {
        val sourceUrl = channel("one")
        subscriptions.add(TEST_USER_ID, subscription("one"))
        val group = (groups.create(TEST_USER_ID, "Work") as SubscriptionGroupWriteResult.Success).group
        groups.addSubscription(TEST_USER_ID, group.id, sourceUrl)
        val channelService = mockk<dev.typetype.server.services.ChannelService>()
        coEvery { channelService.getChannel(sourceUrl, null) } returns SubscriptionFeedTestFixtures.channel(
            SubscriptionFeedTestFixtures.video(1_000L, channel = "different-canonical-uploader"),
        )
        feed = SubscriptionFeedService(subscriptions, channelService, FakeCacheService())

        assertEquals(HttpStatusCode.Accepted, requestFeed(groupId = group.id).status)
        feed.awaitRefresh(TEST_USER_ID)

        assertEquals(1, requestReadyFeed(groupId = group.id).videos.size)
    }

    private suspend fun ApplicationTestBuilder.requestReadyFeed(
        limit: Int = 30,
        groupId: String? = null,
        ungrouped: Boolean = false,
    ): SubscriptionFeedResponse {
        val response = requestFeed(limit = limit, groupId = groupId, ungrouped = ungrouped)
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.requestFeed(
        limit: Int = 30,
        cursor: String? = null,
        groupId: String? = null,
        ungrouped: Boolean = false,
    ): HttpResponse = client.get("/subscriptions/feed") {
        header(HttpHeaders.Authorization, "Bearer test-jwt")
        parameter("limit", limit)
        cursor?.let { parameter("cursor", it) }
        groupId?.let { parameter("groupId", it) }
        if (ungrouped) parameter("ungrouped", true)
    }

    private fun subscription(id: String) = SubscriptionItem(channel(id), id, "")

    private fun channel(id: String) = "https://example.com/channel/$id"
}
