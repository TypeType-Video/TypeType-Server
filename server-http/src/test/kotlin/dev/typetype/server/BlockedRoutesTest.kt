package dev.typetype.server

import dev.typetype.server.routes.blockedRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedService
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

class BlockedRoutesTest {

    private val service = BlockedService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { blockedRoutes(service, auth) }
        }
        block()
    }

    private val urlBody = """{"url":"https://yt.com","name":"Test Channel","thumbnailUrl":"https://thumb.jpg"}"""

    @Test
    fun `GET blocked-channels without token returns 401`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/blocked/channels").status)
    }

    @Test
    fun `GET blocked-channels returns 200 with empty list`() = withApp {
        val response = client.get("/blocked/channels") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `POST blocked-channels returns 201 and persists item`() = withApp {
        val response = client.post("/blocked/channels") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(urlBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"url\":\"https://yt.com\""))
        assertTrue(response.bodyAsText().contains("\"name\":\"Test Channel\""))
        assertTrue(response.bodyAsText().contains("\"thumbnailUrl\":\"https://thumb.jpg\""))
    }

    @Test
    fun `DELETE blocked-channels returns 204 when found`() = withApp {
        service.addChannel(TEST_USER_ID, "https://yt.com")
        assertEquals(HttpStatusCode.NoContent, client.delete("/blocked/channels/https%3A%2F%2Fyt.com") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.status)
    }

    @Test
    fun `GET blocked-videos returns 200 with empty list`() = withApp {
        val response = client.get("/blocked/videos") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `POST blocked-videos returns 201 and persists item`() = withApp {
        val response = client.post("/blocked/videos") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(urlBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"url\":\"https://yt.com\""))
    }

    @Test
    fun `DELETE blocked-videos returns 204 when found`() = withApp {
        service.addVideo(TEST_USER_ID, "https://yt.com")
        assertEquals(HttpStatusCode.NoContent, client.delete("/blocked/videos/https%3A%2F%2Fyt.com") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.status)
    }

    @Test
    fun `DELETE blocked-videos returns 404 when not found`() = withApp {
        assertEquals(HttpStatusCode.NotFound, client.delete("/blocked/videos/https%3A%2F%2Fyt.com") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.status)
    }

    @Test
    fun `blocked keywords are normalized persisted and deleted`() = withApp {
        val created = client.post("/blocked/keywords") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"keyword":"  ClickBait  "}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertTrue(created.bodyAsText().contains("\"keyword\":\"clickbait\""))
        assertTrue(client.get("/blocked/keywords") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText().contains("\"keyword\":\"clickbait\""))
        assertEquals(HttpStatusCode.NoContent, client.delete("/blocked/keywords/clickbait") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.status)
    }

    @Test
    fun `POST blocked keyword rejects blank values`() = withApp {
        val response = client.post("/blocked/keywords") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"keyword":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
