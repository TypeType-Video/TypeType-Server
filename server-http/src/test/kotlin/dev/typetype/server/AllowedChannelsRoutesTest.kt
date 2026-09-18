package dev.typetype.server

import dev.typetype.server.routes.allowedChannelsRoutes
import dev.typetype.server.services.AllowedChannelsService
import dev.typetype.server.services.AuthService
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

class AllowedChannelsRoutesTest {
    private val service = AllowedChannelsService()
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
            routing { allowedChannelsRoutes(service, auth) }
        }
        block()
    }

    @Test
    fun `GET allowed channels without token returns 401`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/allowed/channels").status)
    }

    @Test
    fun `POST allowed channel persists normalized channel`() = withApp {
        val response = client.post("/allowed/channels") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"url":"http://youtube.com/@greatscott/","name":"GreatScott!","thumbnailUrl":"https://thumb.jpg"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"url\":\"https://youtube.com/@greatscott\""))
        val list = client.get("/allowed/channels") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertTrue(list.contains("GreatScott!"))
    }

    @Test
    fun `POST allowed channel is idempotent for same user and url`() = withApp {
        repeat(2) {
            client.post("/allowed/channels") {
                headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
                headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"url":"https://youtube.com/@greatscott","name":"GreatScott!"}""")
            }
        }
        val list = client.get("/allowed/channels") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertEquals(1, list.split("https://youtube.com/@greatscott").size - 1)
    }

    @Test
    fun `DELETE allowed channel removes item`() = withApp {
        service.addChannel(TEST_USER_ID, "https://youtube.com/@greatscott")
        val response = client.delete("/allowed/channels/https%3A%2F%2Fyoutube.com%2F%40greatscott") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        val list = client.get("/allowed/channels") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertEquals("[]", list)
    }
}
