package dev.typetype.server

import dev.typetype.server.SubscriptionFeedTestFixtures.channel
import dev.typetype.server.SubscriptionFeedTestFixtures.subscription
import dev.typetype.server.SubscriptionFeedTestFixtures.video
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.routes.subscriptionFeedRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.SubscriptionFeedService
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionFeedBlockedRoutesTest {
    private lateinit var channelService: ChannelService
    private lateinit var subscriptionsService: SubscriptionsService
    private lateinit var feedService: SubscriptionFeedService
    private val blockedService = BlockedService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        channelService = mockk()
        subscriptionsService = SubscriptionsService()
        feedService = SubscriptionFeedService(subscriptionsService, channelService, FakeCacheService())
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                subscriptionFeedRoutes(
                    feedService,
                    auth,
                    groupsService = SubscriptionGroupsService(),
                    blockedService = blockedService,
                )
            }
        }
        block()
    }

    @Test
    fun `feed drops blocked videos, channels and keywords`() = withApp {
        subscribe()
        blockedService.addVideo(TEST_USER_ID, "https://www.youtube.com/watch?v=blockedvideo")
        blockedService.addChannel(TEST_USER_ID, "https://yt.com/c/Blocked", "Blocked")
        blockedService.addKeyword(TEST_USER_ID, "spoiler")

        val feed = buildAndRead()

        assertEquals(listOf("https://www.youtube.com/watch?v=kept"), feed.videos.map { it.url })
    }

    @Test
    fun `filtered page keeps the cursor of the unfiltered page`() = withApp {
        subscribe()
        blockedService.addVideo(TEST_USER_ID, "https://www.youtube.com/watch?v=blockedvideo")

        val first = buildAndRead(limit = 2)

        assertEquals(listOf("https://www.youtube.com/watch?v=kept"), first.videos.map { it.url })
        val next = first.nextpage
        assertNotNull(next)
        val second = Json.decodeFromString<SubscriptionFeedResponse>(requestFeed(limit = 2, cursor = next).bodyAsText())
        assertEquals(
            listOf("https://www.youtube.com/watch?v=spoilerclip", "https://www.youtube.com/watch?v=otherchannel"),
            second.videos.map { it.url },
        )
    }

    @Test
    fun `ungrouped selection is filtered too`() = withApp {
        subscribe()
        blockedService.addKeyword(TEST_USER_ID, "spoiler")

        val feed = buildAndRead(ungrouped = true)

        assertEquals(
            listOf(
                "https://www.youtube.com/watch?v=blockedvideo",
                "https://www.youtube.com/watch?v=kept",
                "https://www.youtube.com/watch?v=otherchannel",
            ),
            feed.videos.map { it.url },
        )
    }

    private suspend fun subscribe() {
        val mainUrl = "https://yt.com/c/Main"
        val blockedUrl = "https://yt.com/c/Blocked"
        subscriptionsService.add(TEST_USER_ID, subscription(mainUrl, "Main"))
        subscriptionsService.add(TEST_USER_ID, subscription(blockedUrl, "Blocked"))
        coEvery { channelService.getChannel(any(), null) } returns channel()
        coEvery { channelService.getChannel(mainUrl, null) } returns channel(
            video(5000L, channel = "Main", url = "https://www.youtube.com/watch?v=blockedvideo"),
            video(4000L, channel = "Main", url = "https://www.youtube.com/watch?v=kept"),
            video(3000L, channel = "Main", url = "https://www.youtube.com/watch?v=spoilerclip").copy(title = "Big spoiler"),
        )
        coEvery { channelService.getChannel(blockedUrl, null) } returns channel(
            video(2000L, channel = "Blocked", url = "https://www.youtube.com/watch?v=otherchannel"),
        )
    }

    private suspend fun ApplicationTestBuilder.buildAndRead(
        limit: Int = 30,
        ungrouped: Boolean = false,
    ): SubscriptionFeedResponse {
        assertEquals(HttpStatusCode.Accepted, requestFeed(limit, ungrouped = ungrouped).status)
        feedService.awaitRefresh(TEST_USER_ID)
        val response = requestFeed(limit, ungrouped = ungrouped)
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.requestFeed(
        limit: Int,
        cursor: String? = null,
        ungrouped: Boolean = false,
    ): HttpResponse = client.get("/subscriptions/feed") {
        header(HttpHeaders.Authorization, "Bearer test-jwt")
        parameter("limit", limit)
        cursor?.let { parameter("cursor", it) }
        if (ungrouped) parameter("ungrouped", true)
    }
}
