package dev.typetype.server

import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.routes.subscriptionsRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SubscriptionsService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionsDeleteProxyRoutesTest {
    private val service = SubscriptionsService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() {
            TestDatabase.setup()
        }
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { subscriptionsRoutes(service, auth) }
        }
        block()
    }

    @Test
    fun `DELETE subscriptions supports query url`() = withApp {
        service.add(TEST_USER_ID, SubscriptionItem(channelUrl = "https://www.youtube.com/channel/UC123", name = "T", avatarUrl = ""))
        val response = client.delete("/subscriptions?url=https%3A%2F%2Fwww.youtube.com%2Fchannel%2FUC123") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        val body = client.get("/subscriptions") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertFalse(body.contains("UC123"))
    }

    @Test
    fun `DELETE subscriptions repairs proxy-decoded path`() = withApp {
        service.add(TEST_USER_ID, SubscriptionItem(channelUrl = "https://www.youtube.com/channel/UC123", name = "T", avatarUrl = ""))
        val response = client.delete("/subscriptions/https:/www.youtube.com/channel/UC123") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        val body = client.get("/subscriptions") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertFalse(body.contains("UC123"))
    }

    @Test
    fun `DELETE subscriptions accepts query url when provided`() = withApp {
        service.add(TEST_USER_ID, SubscriptionItem(channelUrl = "https://www.youtube.com/channel/UC123", name = "T", avatarUrl = ""))
        val response = client.delete("/subscriptions/https:/www.youtube.com/channel/UC123?url=https%3A%2F%2Fwww.youtube.com%2Fchannel%2FUC123") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }
}
