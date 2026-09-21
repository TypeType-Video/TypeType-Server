package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PlaylistResultItem
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.routes.searchRoutes
import dev.typetype.server.services.SearchService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchRoutesTest {

    private val searchService: SearchService = mockk()

    private fun withApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { searchRoutes(searchService) }
            }
            block()
        }

    @Test
    fun `GET search without q returns 400`() = withApp {
        val response = client.get("/search?service=1")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET search without service returns 400`() = withApp {
        val response = client.get("/search?q=test")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET search with invalid service returns 400`() = withApp {
        val response = client.get("/search?q=test&service=notanumber")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET search returns 200 on Success`() = withApp {
        coEvery { searchService.search(any(), any(), any(), any(), any()) } returns
            ExtractionResult.Success(SearchPageResponse(items = emptyList(), nextpage = null, searchSuggestion = null, isCorrectedSearch = false))
        val response = client.get("/search?q=test&service=0")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET search returns playlist results`() = withApp {
        coEvery { searchService.search(any(), any(), any(), any(), any()) } returns ExtractionResult.Success(
            SearchPageResponse(
                items = emptyList(),
                nextpage = null,
                searchSuggestion = null,
                isCorrectedSearch = false,
                playlists = listOf(
                    PlaylistResultItem(
                        id = "playlist-id",
                        title = "Playlist",
                        url = "https://youtube.com/playlist?list=playlist-id",
                        thumbnailUrl = "https://img.youtube.com/playlist.jpg",
                        uploaderName = "Uploader",
                        streamCount = 12L,
                        playlistType = "normal",
                    )
                ),
            )
        )

        val response = client.get("/search?q=test&service=0")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"playlists\""))
        assertTrue(body.contains("\"streamCount\":12"))
    }

    @Test
    fun `GET search returns 422 on Failure`() = withApp {
        coEvery { searchService.search(any(), any(), any(), any(), any()) } returns
            ExtractionResult.Failure("error")
        val response = client.get("/search?q=test&service=0")
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET search returns 400 on BadRequest`() = withApp {
        coEvery { searchService.search(any(), any(), any(), any(), any()) } returns
            ExtractionResult.BadRequest("bad")
        val response = client.get("/search?q=test&service=0")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
