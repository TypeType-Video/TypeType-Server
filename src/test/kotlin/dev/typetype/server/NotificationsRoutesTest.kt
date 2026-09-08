package dev.typetype.server

import dev.typetype.server.SubscriptionFeedTestFixtures.channel
import dev.typetype.server.SubscriptionFeedTestFixtures.subscription
import dev.typetype.server.SubscriptionFeedTestFixtures.video
import dev.typetype.server.routes.notificationsRoutes
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.NotificationReadItemsTable
import dev.typetype.server.db.tables.NotificationStatesTable
import dev.typetype.server.models.NotificationsResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.NotificationsService
import dev.typetype.server.services.SubscriptionFeedAvailability
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.insert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationsRoutesTest {
    private lateinit var channelService: ChannelService
    private lateinit var subscriptionsService: SubscriptionsService
    private lateinit var notificationsService: NotificationsService
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        channelService = mockk()
        subscriptionsService = SubscriptionsService()
        val subscriptionFeedService = SubscriptionFeedService(
            subscriptionsService,
            channelService,
            FakeCacheService(),
        )
        notificationsService = NotificationsService(subscriptionFeedService)
    }
    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { notificationsRoutes(notificationsService, auth) }
        }
        block()
    }

    @Test
    fun `GET notifications requires auth`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/notifications").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/notifications/unread-count").status)
    }

    @Test
    fun `GET notifications returns every new video with service identity`() = withApp {
        subscriptionsService.add(TEST_USER_ID, subscription("https://yt.com/c/a", "A"))
        subscriptionsService.add(TEST_USER_ID, subscription("https://yt.com/c/b", "B"))
        coEvery { channelService.getChannel("https://yt.com/c/a", null) } returns channel(
            video(1000L, "A", "https://www.youtube.com/watch?v=yt-old"),
            video(3000L, "A", "https://www.youtube.com/watch?v=yt-new"),
        )
        coEvery { channelService.getChannel("https://yt.com/c/b", null) } returns channel(
            video(2000L, "A", "https://www.bilibili.com/video/av2000"),
            video(4000L, "A", "https://www.nicovideo.jp/watch/sm4000"),
        )
        val body = client.get("/notifications?page=0&limit=10") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(body.contains("\"unreadCount\":4"))
        assertTrue(body.indexOf("4000") < body.indexOf("3000"))
        assertTrue(body.contains("yt-new"))
        assertTrue(body.contains("yt-old"))
        assertTrue(body.contains("\"serviceName\":\"BiliBili\""))
        assertTrue(body.contains("\"serviceName\":\"NicoNico\""))
    }

    @Test
    fun `POST notifications read-all clears unread count`() = withApp {
        subscriptionsService.add(TEST_USER_ID, subscription("https://yt.com/c/a", "A"))
        coEvery { channelService.getChannel("https://yt.com/c/a", null) } returns channel(video(3000L, "A"))
        val before = client.get("/notifications") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(before.contains("\"unreadCount\":1"))
        val mark = client.post("/notifications/read-all") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, mark.status)
        val after = client.get("/notifications") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(after.contains("\"unreadCount\":0"))
    }

    @Test
    fun `GET notifications unread-count returns unread count`() = withApp {
        subscriptionsService.add(TEST_USER_ID, subscription("https://yt.com/c/a", "A"))
        coEvery { channelService.getChannel("https://yt.com/c/a", null) } returns channel(video(4000L, "A"))

        val before = client.get("/notifications/unread-count") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(before.contains("\"unreadCount\":1"))

        client.post("/notifications/read-all") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }

        val after = client.get("/notifications/unread-count") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(after.contains("\"unreadCount\":0"))
    }

    @Test
    fun `read watermark remains effective when one notification has an explicit read row`() = withApp {
        subscriptionsService.add(TEST_USER_ID, subscription("https://yt.com/c/a", "A"))
        coEvery { channelService.getChannel("https://yt.com/c/a", null) } returns channel(
            video(1000L, "A", "https://www.youtube.com/watch?v=old"),
            video(2000L, "A", "https://www.youtube.com/watch?v=new"),
        )
        val initial = Json.decodeFromString<NotificationsResponse>(client.get("/notifications?page=0&limit=10") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText())
        val latest = initial.items.maxBy { it.createdAt }
        DatabaseFactory.query {
            NotificationStatesTable.insert {
                it[userId] = TEST_USER_ID
                it[subscriptionLastSeenUploaded] = initial.items.maxOf { item -> item.createdAt }
                it[updatedAt] = System.currentTimeMillis()
            }
            NotificationReadItemsTable.insert {
                it[userId] = TEST_USER_ID
                it[notificationId] = latest.id
                it[readAt] = System.currentTimeMillis()
            }
        }
        val result = Json.decodeFromString<NotificationsResponse>(client.get("/notifications?page=0&limit=10") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText())
        assertTrue(result.items.all { it.read })
        assertEquals(0, result.unreadCount)
    }

    @Test
    fun `unread count reports unavailable instead of trusting a fresh cache`() = runTest {
        val feed = mockk<SubscriptionFeedService>()
        val availableVideo = video(1000L, "A")
        coEvery { feed.getAllWithAvailability(TEST_USER_ID) } returnsMany listOf(
            SubscriptionFeedAvailability(listOf(availableVideo), true),
            SubscriptionFeedAvailability(emptyList(), false),
        )
        val service = NotificationsService(feed)
        assertEquals(1, service.getUnreadCount(TEST_USER_ID).unreadCount)
        val unavailable = service.getUnreadCount(TEST_USER_ID)
        assertEquals(1, unavailable.unreadCount)
        assertTrue(!unavailable.available)
    }
}
