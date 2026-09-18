package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PublicPlaylistItem
import dev.typetype.server.models.PublicPlaylistResponse
import dev.typetype.server.routes.publicPlaylistRoutes
import dev.typetype.server.services.PublicPlaylistService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublicPlaylistRoutesTest {
    private val playlistService: PublicPlaylistService = mockk()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { publicPlaylistRoutes(playlistService) }
        }
        block()
    }

    @Test
    fun `GET playlist without url returns 400`() = withApp {
        val response = client.get("/playlist")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET playlist returns public playlist response`() = withApp {
        coEvery { playlistService.getPlaylist(any(), any()) } returns ExtractionResult.Success(testPlaylistResponse())

        val response = client.get("/playlist?url=https://youtube.com/playlist?list=test&nextpage=cursor")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"playlist\""))
        coVerify { playlistService.getPlaylist("https://youtube.com/playlist?list=test", "cursor") }
    }

    @Test
    fun `GET playlist returns 422 on extraction failure`() = withApp {
        coEvery { playlistService.getPlaylist(any(), any()) } returns ExtractionResult.Failure("error")

        val response = client.get("/playlist?url=https://youtube.com/playlist?list=test")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    private fun testPlaylistResponse(): PublicPlaylistResponse = PublicPlaylistResponse(
        playlist = PublicPlaylistItem(
            id = "https://youtube.com/playlist?list=test",
            title = "Test Playlist",
            url = "https://youtube.com/playlist?list=test",
            thumbnailUrl = "",
            uploaderName = "Test Channel",
            streamCount = 1L,
            playlistType = "normal",
        ),
        videos = emptyList(),
        nextpage = null,
    )
}
