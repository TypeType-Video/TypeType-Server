package dev.typetype.server

import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.routes.subscriptionsRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SubscriptionsService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionsCanonicalizationRoutesTest {
    private val service = SubscriptionsService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
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
    fun `POST and GET subscriptions return canonical channel url`() = withApp {
        val response = client.post("/subscriptions") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"channelUrl":"http://WWW.YouTube.com/channel/UC123/?utm_source=x#frag","name":"Test","avatarUrl":""}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"channelUrl\":\"https://www.youtube.com/channel/UC123\""))
        val listBody = client.get("/subscriptions") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(listBody.contains("\"channelUrl\":\"https://www.youtube.com/channel/UC123\""))
    }

    @Test
    fun `DELETE subscriptions canonicalizes channel url`() = withApp {
        service.add(
            TEST_USER_ID,
            SubscriptionItem(channelUrl = "https://www.youtube.com/channel/UC123", name = "Test", avatarUrl = ""),
        )
        val deleteResponse = client.delete("/subscriptions/http%3A%2F%2FWWW.YouTube.com%2Fchannel%2FUC123%2F%3Futm_source%3Dx%23frag") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
    }
}
