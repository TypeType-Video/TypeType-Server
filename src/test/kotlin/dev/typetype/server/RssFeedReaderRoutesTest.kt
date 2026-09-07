package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.models.ChannelPlaylistsResponse
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.RssFeedRequest
import dev.typetype.server.routes.rssPublicRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.RssFeedManagementService
import dev.typetype.server.services.RssFeedReaderService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class RssFeedReaderRoutesTest {
    private val settings = AdminSettingsService()
    private val subscriptions = SubscriptionsService()
    private val feed = SubscriptionFeedService(subscriptions, FakeChannelService(), FakeCacheService())
    private val blocked = BlockedService()
    private val management = RssFeedManagementService(settings, subscriptions)
    private val reader = RssFeedReaderService(settings, feed, blocked)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        insertUser()
    }

    @Test
    fun `private feed supports conditional cache and immediate secret revocation`() = testApplication {
        val created = createFeed()
        val uri = URI(created.feedUrl)
        application {
            install(ContentNegotiation) { json() }
            routing { rssPublicRoutes(reader) }
        }

        val first = client.get(uri.rawPath.removePrefix("/api") + "?" + uri.rawQuery)
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals("application/rss+xml; charset=utf-8", first.headers[HttpHeaders.ContentType])
        assertEquals("private, max-age=300, must-revalidate", first.headers[HttpHeaders.CacheControl])
        assertTrue(first.bodyAsText().contains("<rss version=\"2.0\""))
        assertTrue(first.bodyAsText().contains("https://video.example/watch?v="))
        val etag = first.headers[HttpHeaders.ETag]!!
        val lastModified = first.headers[HttpHeaders.LastModified]!!

        val cached = client.get(uri.rawPath.removePrefix("/api") + "?" + uri.rawQuery) {
            headers.append(HttpHeaders.IfNoneMatch, "W/$etag")
        }
        assertEquals(HttpStatusCode.NotModified, cached.status)

        val changed = client.get(uri.rawPath.removePrefix("/api") + "?" + uri.rawQuery) {
            headers.append(HttpHeaders.IfNoneMatch, "\"different\"")
            headers.append(HttpHeaders.IfModifiedSince, lastModified)
        }
        assertEquals(HttpStatusCode.OK, changed.status)

        val regenerated = management.regenerate("user-a", created.feed.id)
        assertEquals(HttpStatusCode.NotFound, client.get(uri.rawPath.removePrefix("/api") + "?" + uri.rawQuery).status)
        assertFalse(regenerated.feedUrl.endsWith(uri.rawQuery))
    }

    @Test
    fun `blocked videos channels and keywords are excluded from RSS output`() = testApplication {
        val created = createFeed()
        val uri = URI(created.feedUrl)
        val path = uri.rawPath.removePrefix("/api") + "?" + uri.rawQuery
        application {
            install(ContentNegotiation) { json() }
            routing { rssPublicRoutes(reader) }
        }

        blocked.addVideo("user-a", "https://youtube.com/@a/video")
        assertTrue(blocked.profileFor("user-a").blocksVideo("https://youtube.com/@a/video"))
        val blockedVideoUrl = URLEncoder.encode("https://youtube.com/@a/video", StandardCharsets.UTF_8)
        assertFalse(client.get(path).bodyAsText().contains(blockedVideoUrl))
        blocked.deleteVideo("user-a", "https://youtube.com/@a/video", "user")

        blocked.addChannel("user-a", "https://youtube.com/@a")
        assertTrue(blocked.profileFor("user-a").blocksChannel("https://youtube.com/@a", "A"))
        assertFalse(client.get(path).bodyAsText().contains("<item>"))
        blocked.deleteChannel("user-a", "https://youtube.com/@a", "user")

        blocked.addKeyword("user-a", "video")
        assertFalse(client.get(path).bodyAsText().contains("<item>"))
    }

    @Test
    fun `RSS reads an existing snapshot without starting extraction`() = testApplication {
        val channel = CountingChannelService()
        val snapshotOnlyFeed = SubscriptionFeedService(subscriptions, channel, FakeCacheService())
        val snapshotOnlyReader = RssFeedReaderService(settings, snapshotOnlyFeed, blocked)
        val created = createUnprimedFeed()
        val uri = URI(created.feedUrl)
        application { routing { rssPublicRoutes(snapshotOnlyReader) } }

        val response = client.get(uri.rawPath.removePrefix("/api") + "?" + uri.rawQuery)

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(response.bodyAsText().contains("<item>"))
        assertEquals(0, channel.calls)
    }

    @Test
    fun `RSS enforces configured item and request limits`() = testApplication {
        settings.upsert(
            AdminSettingsItem(
                rssEnabled = true,
                rssPublicBaseUrl = "https://video.example",
                rssMaxItems = 1,
                rssRateLimitPerMinute = 1,
            ),
        )
        subscriptions.add("user-a", SubscriptionFeedTestFixtures.subscription("https://youtube.com/@a", "A"))
        subscriptions.add("user-a", SubscriptionFeedTestFixtures.subscription("https://youtube.com/@b", "B"))
        val created = management.create("user-a", RssFeedRequest(name = "Limited feed"))
        feed.getAll("user-a")
        feed.awaitRefresh("user-a")
        val uri = URI(created.feedUrl)
        val path = uri.rawPath.removePrefix("/api") + "?" + uri.rawQuery
        application {
            install(ContentNegotiation) { json() }
            routing { rssPublicRoutes(reader) }
        }

        val first = client.get(path)
        assertEquals(1, "<item>".toRegex().findAll(first.bodyAsText()).count())
        val throttled = client.get(path)
        assertEquals(HttpStatusCode.TooManyRequests, throttled.status)
        assertTrue(throttled.headers[HttpHeaders.RetryAfter]?.toIntOrNull() in 1..60)
    }

    @Test
    fun `RSS rejects feeds owned by suspended accounts`() = testApplication {
        val created = createFeed()
        transaction {
            UsersTable.update({ UsersTable.id eq "user-a" }) { it[suspended] = true }
        }
        val uri = URI(created.feedUrl)
        application { routing { rssPublicRoutes(reader) } }

        val response = client.get(uri.rawPath.removePrefix("/api") + "?" + uri.rawQuery)

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private suspend fun createFeed(): dev.typetype.server.models.RssFeedSecretItem {
        val created = createUnprimedFeed()
        feed.getAll("user-a")
        feed.awaitRefresh("user-a")
        return created
    }

    private suspend fun createUnprimedFeed(): dev.typetype.server.models.RssFeedSecretItem {
        settings.upsert(AdminSettingsItem(rssEnabled = true, rssPublicBaseUrl = "https://video.example"))
        subscriptions.add("user-a", SubscriptionFeedTestFixtures.subscription("https://youtube.com/@a", "A"))
        return management.create("user-a", RssFeedRequest(name = "My feed"))
    }

    private fun insertUser() = transaction {
        UsersTable.insert {
            it[id] = "user-a"
            it[email] = "rss-reader@test.local"
            it[passwordHash] = "hash"
            it[name] = "RSS reader"
            it[role] = "user"
            it[createdAt] = 1L
            it[updatedAt] = 1L
        }
    }

    private class CountingChannelService : ChannelService {
        var calls = 0

        override suspend fun getChannel(
            url: String,
            nextpage: String?,
            sort: String?,
        ): ExtractionResult<ChannelResponse> {
            calls += 1
            return ExtractionResult.Success(ChannelResponse("", "", "", "", 0, false, emptyList(), null))
        }

        override suspend fun getPlaylists(
            url: String,
            nextpage: String?,
        ): ExtractionResult<ChannelPlaylistsResponse> =
            ExtractionResult.Success(ChannelPlaylistsResponse(emptyList(), null))
    }
}
