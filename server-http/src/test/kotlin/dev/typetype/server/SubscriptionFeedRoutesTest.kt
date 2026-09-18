package dev.typetype.server

import dev.typetype.server.SubscriptionFeedTestFixtures.channel
import dev.typetype.server.SubscriptionFeedTestFixtures.subscription
import dev.typetype.server.SubscriptionFeedTestFixtures.video
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.routes.subscriptionFeedRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.SubscriptionFeedService
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
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class SubscriptionFeedRoutesTest {
    private lateinit var channelService: ChannelService
    private lateinit var cacheService: FakeCacheService
    private lateinit var subscriptionsService: SubscriptionsService
    private lateinit var feedService: SubscriptionFeedService
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        channelService = mockk()
        cacheService = FakeCacheService()
        subscriptionsService = SubscriptionsService()
        feedService = SubscriptionFeedService(subscriptionsService, channelService, cacheService)
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { subscriptionFeedRoutes(feedService, auth) }
        }
        block()
    }

    @Test
    fun `GET subscriptions feed without token returns 401`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/subscriptions/feed").status)
    }

    @Test
    fun `cold feed with one slow source returns bounded 202`() = withApp {
        repeat(100) { subscriptionsService.add(TEST_USER_ID, subscription(it + 1)) }
        val slowSource = CompletableDeferred<Unit>()
        coEvery { channelService.getChannel(any(), null) } coAnswers {
            if (firstArg<String>().endsWith("/100")) slowSource.await()
            channel(video(1000L))
        }
        lateinit var response: HttpResponse
        val elapsed = measureTimeMillis { response = requestFeed() }
        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals("1", response.headers[HttpHeaders.RetryAfter])
        assertTrue(response.bodyAsText().contains("subscription_feed_preparing"))
        assertTrue(elapsed < 500, "cold response took ${elapsed}ms")
        slowSource.complete(Unit)
        feedService.awaitRefresh(TEST_USER_ID)
        assertEquals(HttpStatusCode.OK, requestFeed().status)
    }

    @Test
    fun `concurrent cold requests share one refresh`() = withApp {
        subscriptionsService.add(TEST_USER_ID, subscription(1))
        coEvery { channelService.getChannel(any(), null) } coAnswers {
            delay(100)
            channel(video(1000L))
        }
        val responses = coroutineScope { List(12) { async { requestFeed() } }.map { it.await() } }
        assertTrue(responses.all { it.status == HttpStatusCode.Accepted })
        feedService.awaitRefresh(TEST_USER_ID)
        coVerify(exactly = 1) { channelService.getChannel("https://yt.com/c/1", null) }
    }

    @Test
    fun `ready feed promotes a newly observed live and keeps unknown dates last`() = withApp {
        val channelUrl = "https://www.youtube.com/channel/UC1"
        subscriptionsService.add(TEST_USER_ID, subscription(channelUrl, "Live channel"))
        coEvery { channelService.getChannel(channelUrl, null) } returns channel(
            video(3000L, url = "https://www.youtube.com/watch?v=normal"),
            video(2000L, url = "https://www.youtube.com/watch?v=live"),
        )
        coEvery { channelService.getChannel("$channelUrl/streams", null) } returns channel(
            video(-1L, url = "https://www.youtube.com/watch?v=live", live = true),
            video(-1L, url = "https://www.youtube.com/watch?v=unknown"),
        )
        val feed = buildAndRead()
        assertTrue(feed.videos.first().isLive)
        assertEquals(1, feed.videos.count { it.url.endsWith("v=live") })
        assertTrue(feed.videos.last().url.endsWith("v=unknown"))
        assertNotNull(feed.generation)
        assertNotNull(feed.generatedAt)
    }

    @Test
    fun `stale live from channel page is absent when streams tab no longer lists it`() = withApp {
        val channelUrl = "https://www.youtube.com/channel/UC1"
        val staleLiveUrl = "https://www.youtube.com/watch?v=private"
        subscriptionsService.add(TEST_USER_ID, subscription(channelUrl, "Live channel"))
        coEvery { channelService.getChannel(channelUrl, null) } returns channel(
            video(-1L, url = staleLiveUrl, live = true),
            video(3000L, url = "https://www.youtube.com/watch?v=normal"),
        )
        coEvery { channelService.getChannel("$channelUrl/streams", null) } returns channel()

        val feed = buildAndRead()

        assertEquals(listOf("https://www.youtube.com/watch?v=normal"), feed.videos.map { it.url })
    }

    @Test
    fun `cursor keeps pagination on one generation after refresh`() = withApp {
        subscriptionsService.add(TEST_USER_ID, subscription(1))
        var newest = 5_000L
        coEvery { channelService.getChannel(any(), null) } coAnswers {
            channel(video(newest), video(4000L), video(3000L))
        }
        val first = buildAndRead(limit = 2)
        val cursor = requireNotNull(first.nextpage)
        newest = 9_000L
        feedService.invalidate(TEST_USER_ID)
        feedService.awaitRefresh(TEST_USER_ID)
        val second = requestFeed(limit = 2, cursor = cursor)
        assertEquals(HttpStatusCode.OK, second.status)
        val page = Json.decodeFromString<SubscriptionFeedResponse>(second.bodyAsText())
        assertEquals(first.generation, page.generation)
        assertEquals(listOf(3000L), page.videos.map { it.uploaded })
    }

    @Test
    fun `cursor older than retained generation returns typed 409`() = withApp {
        subscriptionsService.add(TEST_USER_ID, subscription(1))
        var newest = 5_000L
        coEvery { channelService.getChannel(any(), null) } coAnswers {
            channel(video(newest), video(1000L))
        }
        val cursor = requireNotNull(buildAndRead(limit = 1).nextpage)
        repeat(2) {
            newest += 1_000
            feedService.invalidate(TEST_USER_ID)
            feedService.awaitRefresh(TEST_USER_ID)
        }
        val response = requestFeed(limit = 1, cursor = cursor)
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.bodyAsText().contains("subscription_feed_stale_generation"))
    }

    @Test
    fun `stale feed returns immediately while one refresh runs`() = withApp {
        subscriptionsService.add(TEST_USER_ID, subscription(1))
        val gate = CompletableDeferred<Unit>()
        var rebuilding = false
        coEvery { channelService.getChannel(any(), null) } coAnswers {
            if (rebuilding) gate.await()
            channel(video(if (rebuilding) 2000L else 1000L))
        }
        buildAndRead()
        rebuilding = true
        feedService.invalidate(TEST_USER_ID)
        lateinit var response: HttpResponse
        val elapsed = measureTimeMillis { response = requestFeed() }
        val stale = Json.decodeFromString<SubscriptionFeedResponse>(response.bodyAsText())
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1000L, stale.videos.single().uploaded)
        assertTrue(stale.refreshing)
        assertTrue(elapsed < 500, "stale response took ${elapsed}ms")
        gate.complete(Unit)
        feedService.awaitRefresh(TEST_USER_ID)
    }

    @Test
    fun `partial refresh publishes videos from successful sources`() = withApp {
        subscriptionsService.add(TEST_USER_ID, subscription(1))
        subscriptionsService.add(TEST_USER_ID, subscription(2))
        var rebuilding = false
        coEvery { channelService.getChannel("https://yt.com/c/1", null) } coAnswers {
            channel(video(if (rebuilding) 9000L else 1000L))
        }
        coEvery { channelService.getChannel("https://yt.com/c/2", null) } coAnswers {
            if (rebuilding) error("channel unavailable") else channel(video(2000L))
        }
        val original = buildAndRead()
        rebuilding = true
        feedService.invalidate(TEST_USER_ID)
        feedService.awaitRefresh(TEST_USER_ID)
        val refreshed = Json.decodeFromString<SubscriptionFeedResponse>(requestFeed().bodyAsText())
        assertTrue(refreshed.generation != original.generation)
        assertEquals(listOf(9000L), refreshed.videos.map { it.uploaded })
    }

    @Test
    fun `refresh keeps previous snapshot when every source fails`() = withApp {
        subscriptionsService.add(TEST_USER_ID, subscription(1))
        var rebuilding = false
        coEvery { channelService.getChannel(any(), null) } coAnswers {
            if (rebuilding) error("channel unavailable") else channel(video(1000L))
        }
        val original = buildAndRead()
        rebuilding = true
        feedService.invalidate(TEST_USER_ID)
        feedService.awaitRefresh(TEST_USER_ID)
        val retained = Json.decodeFromString<SubscriptionFeedResponse>(requestFeed().bodyAsText())
        assertEquals(original.generation, retained.generation)
        assertEquals(listOf(1000L), retained.videos.map { it.uploaded })
    }

    @Test
    fun `invalid cursor returns typed 400`() = withApp {
        coEvery { channelService.getChannel(any(), null) } returns channel(video(1000L))
        buildAndRead()
        val response = requestFeed(cursor = "not-a-cursor")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("subscription_feed_invalid_cursor"))
    }

    private suspend fun ApplicationTestBuilder.buildAndRead(limit: Int = 30): SubscriptionFeedResponse {
        assertEquals(HttpStatusCode.Accepted, requestFeed(limit).status)
        feedService.awaitRefresh(TEST_USER_ID)
        val response = requestFeed(limit)
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.requestFeed(limit: Int = 30, cursor: String? = null): HttpResponse =
        client.get("/subscriptions/feed") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            parameter("limit", limit)
            cursor?.let { parameter("cursor", it) }
        }
}
