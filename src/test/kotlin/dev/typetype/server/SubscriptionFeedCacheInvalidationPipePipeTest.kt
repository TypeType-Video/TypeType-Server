package dev.typetype.server

import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.routes.restoreRoutes
import dev.typetype.server.routes.subscriptionFeedRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PipePipeBackupImporterService
import dev.typetype.server.services.SubscriptionFeedCacheInvalidation
import dev.typetype.server.services.SubscriptionFeedCacheInvalidatorImpl
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class SubscriptionFeedCacheInvalidationPipePipeTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    private val cache = FakeCacheService()
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val subscriptions = SubscriptionsService()
    private val channel = FakeChannelService()
    private val feed = SubscriptionFeedService(subscriptions, channel, cache)
    private val restore = PipePipeBackupImporterService()

    @BeforeEach
    fun clean() = runBlocking {
        TestDatabase.truncateAll()
        cache.clear()
        SubscriptionFeedCacheInvalidation.configure(SubscriptionFeedCacheInvalidatorImpl(cache, feed))
    }

    @Test
    fun `pipepipe restore invalidates cached feed`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                subscriptionFeedRoutes(feed, auth)
                restoreRoutes(restore, auth)
            }
        }

        subscriptions.add(TEST_USER_ID, SubscriptionItem("https://yt.com/c/pre", "Pre", ""))
        val before = client.get("/subscriptions/feed") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.Accepted, before.status)
        feed.awaitRefresh(TEST_USER_ID)
        assertTrue(
            client.get("/subscriptions/feed") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
                .bodyAsText().contains("pre/video"),
        )

        val zip = PipePipeBackupTestFixtures.createBackupZip()
        val payload = Files.readAllBytes(zip)
        val restoreResp = client.post("/restore/pipepipe") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", payload, Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                            append(HttpHeaders.ContentDisposition, "filename=pipepipe.zip")
                        })
                    },
                ),
            )
        }
        Files.deleteIfExists(zip)
        assertEquals(HttpStatusCode.OK, restoreResp.status)

        val after = client.get("/subscriptions/feed") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, after.status)
        feed.awaitRefresh(TEST_USER_ID)
        val refreshed = client.get("/subscriptions/feed") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertTrue(refreshed.bodyAsText().contains("youtube.com/@x/video"))
    }
}
