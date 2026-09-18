package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PublicPlaylistItem
import dev.typetype.server.models.PublicPlaylistResponse
import dev.typetype.server.models.SavedPlaylistItem
import dev.typetype.server.routes.savedPlaylistRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PublicPlaylistService
import dev.typetype.server.services.SavedPlaylistService
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
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val PLAYLIST_URL = "https://youtube.com/playlist?list=PL123"

class SavedPlaylistRoutesTest {
    private val savedPlaylistService = SavedPlaylistService()
    private val publicPlaylistService: PublicPlaylistService = mockk()
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
            routing { savedPlaylistRoutes(savedPlaylistService, publicPlaylistService, auth) }
        }
        block()
    }

    @Test
    fun `POST saved playlists extracts and persists public playlist`() = withApp {
        coEvery { publicPlaylistService.getPlaylist(PLAYLIST_URL, null) } returns success(title = "Test Playlist")

        assertEquals("[]", authedGet().bodyAsText())
        val response = authedPost(PLAYLIST_URL)

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"title\":\"Test Playlist\""))
        assertTrue(authedGet().bodyAsText().contains("\"title\":\"Test Playlist\""))
    }

    @Test
    fun `POST saved playlists updates duplicate instead of creating another row`() = withApp {
        coEvery { publicPlaylistService.getPlaylist(PLAYLIST_URL, null) } returnsMany listOf(
            success(title = "Old title"),
            success(title = "New title"),
        )

        authedPost(PLAYLIST_URL)
        authedPost(PLAYLIST_URL)
        val items = Json.decodeFromString<List<SavedPlaylistItem>>(authedGet().bodyAsText())

        assertEquals(1, items.size)
        assertEquals("New title", items.single().title)
    }

    @Test
    fun `DELETE saved playlist removes saved item`() = withApp {
        val saved = savedPlaylistService.save(TEST_USER_ID, publicPlaylist())

        val response = client.delete("/saved-playlists/${saved.id}") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("[]", authedGet().bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.authedGet() =
        client.get("/saved-playlists") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }

    private suspend fun ApplicationTestBuilder.authedPost(url: String) =
        client.post("/saved-playlists") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"url":"$url"}""")
        }

    private fun success(title: String): ExtractionResult<PublicPlaylistResponse> =
        ExtractionResult.Success(PublicPlaylistResponse(publicPlaylist(title), emptyList(), null))

    private fun publicPlaylist(title: String = "Test Playlist"): PublicPlaylistItem = PublicPlaylistItem(
        id = "PL123",
        title = title,
        url = PLAYLIST_URL,
        thumbnailUrl = "https://img.test/pl.jpg",
        uploaderName = "Creator",
        streamCount = 42L,
        playlistType = "normal",
    )
}
