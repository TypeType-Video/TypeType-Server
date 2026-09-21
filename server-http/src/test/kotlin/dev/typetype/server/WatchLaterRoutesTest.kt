package dev.typetype.server

import dev.typetype.server.models.WatchLaterItem
import dev.typetype.server.routes.watchLaterRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.WatchLaterService
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

class WatchLaterRoutesTest {

    private val service = WatchLaterService()
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
            routing { watchLaterRoutes(service, auth) }
        }
        block()
    }

    private val itemBody = """{"url":"https://yt.com","title":"Test","thumbnail":"","duration":100}"""

    @Test
    fun `GET watch-later without token returns 401`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/watch-later").status)
    }

    @Test
    fun `GET watch-later returns 200 with empty list`() = withApp {
        val response = client.get("/watch-later") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `POST watch-later returns 201 and persists item`() = withApp {
        val response = client.post("/watch-later") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(itemBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"url\":\"https://yt.com\""))
    }

    @Test
    fun `GET watch-later returns persisted items`() = withApp {
        service.add(TEST_USER_ID, WatchLaterItem(url = "https://yt.com", title = "Test", thumbnail = "", duration = 100L))
        val body = client.get("/watch-later") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertTrue(body.contains("\"url\":\"https://yt.com\""))
    }

    @Test
    fun `DELETE watch-later returns 204 when found`() = withApp {
        service.add(TEST_USER_ID, WatchLaterItem(url = "https://yt.com", title = "Test", thumbnail = "", duration = 100L))
        assertEquals(HttpStatusCode.NoContent, client.delete("/watch-later/https%3A%2F%2Fyt.com") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.status)
    }

    @Test
    fun `DELETE watch-later keeps decoded youtube query in url`() = withApp {
        val videoUrl = "https://www.youtube.com/watch?v=test123"
        service.add(
            TEST_USER_ID,
            WatchLaterItem(url = videoUrl, title = "Test", thumbnail = "", duration = 100L),
        )
        val response = client.delete("/watch-later/https://www.youtube.com/watch?v=test123") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `DELETE watch-later returns 404 when not found`() = withApp {
        assertEquals(HttpStatusCode.NotFound, client.delete("/watch-later/https%3A%2F%2Fyt.com") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.status)
    }
}
