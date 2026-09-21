package dev.typetype.server

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.routes.playlistRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PlaylistService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
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

class PlaylistRoutesTest {
    private val service = PlaylistService()
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
            routing { playlistRoutes(service, auth) }
        }
        block()
    }

    private val playlistBody = """{"name":"My Playlist","description":""}"""
    private val videoBody = """{"url":"https://yt.com","title":"T","thumbnailUrl":"thumb","duration":100,"uploaderName":"C","uploaderUrl":"https://c","uploaderAvatarUrl":"avatar","viewCount":123}"""

    @Test
    fun `GET playlists without token returns 401`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/playlists").status)
    }

    @Test
    fun `GET playlists returns 200 with empty list`() = withApp {
        val response = client.get("/playlists") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `POST playlists returns 201 and persists item`() = withApp {
        val response = client.post("/playlists") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(playlistBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"name\":\"My Playlist\""))
    }
    @Test
    fun `GET playlists by id returns 200 when found`() = withApp {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "My Playlist"))
        assertEquals(HttpStatusCode.OK, client.get("/playlists/${playlist.id}") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.status)
    }

    @Test
    fun `GET playlists by id returns 404 when not found`() = withApp {
        assertEquals(HttpStatusCode.NotFound, client.get("/playlists/nonexistent") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.status)
    }

    @Test
    fun `PUT playlists returns 204 when found`() = withApp {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "Old"))
        assertEquals(HttpStatusCode.NoContent, client.put("/playlists/${playlist.id}") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(playlistBody)
        }.status)
    }

    @Test
    fun `DELETE playlists returns 204 when found`() = withApp {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "My Playlist"))
        assertEquals(HttpStatusCode.NoContent, client.delete("/playlists/${playlist.id}") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.status)
    }

    @Test
    fun `POST playlists videos returns 201`() = withApp {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "My Playlist"))
        val response = client.post("/playlists/${playlist.id}/videos") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(videoBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"channelName\":\"C\""))
    }

    @Test
    fun `DELETE playlists video returns 204 when found`() = withApp {
        val playlist = service.create(TEST_USER_ID, PlaylistItem(name = "My Playlist"))
        service.addVideo(TEST_USER_ID, playlist.id, dev.typetype.server.models.PlaylistVideoItem(url = "https://yt.com", title = "T", thumbnail = "", duration = 100L))
        assertEquals(HttpStatusCode.NoContent, client.delete("/playlists/${playlist.id}/videos/https%3A%2F%2Fyt.com") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.status)
    }
}
