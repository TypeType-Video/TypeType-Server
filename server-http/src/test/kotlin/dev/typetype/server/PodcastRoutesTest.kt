package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PodcastEpisodesResponse
import dev.typetype.server.models.PodcastItem
import dev.typetype.server.models.PodcastPageResponse
import dev.typetype.server.routes.podcastRoutes
import dev.typetype.server.services.PodcastService
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PodcastRoutesTest {

    private val podcastService: PodcastService = mockk()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { podcastRoutes(podcastService) }
        }
        block()
    }

    private fun testPodcastResponse() = PodcastPageResponse(
        channelName = "Test Channel",
        channelUrl = "https://youtube.com/@test",
        podcasts = emptyList(),
        episodes = emptyList(),
        nextpage = null,
    )

    private fun testPodcastEpisodesResponse() = PodcastEpisodesResponse(
        podcast = PodcastItem(
            id = "https://youtube.com/playlist?list=test",
            title = "Test Podcast",
            url = "https://youtube.com/playlist?list=test",
            thumbnailUrl = "",
            uploaderName = "Test Channel",
            streamCount = 1L,
            playlistType = "normal",
        ),
        episodes = emptyList(),
        nextpage = null,
    )

    @Test
    fun `GET podcasts without url returns 400`() = withApp {
        val response = client.get("/podcasts")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET podcasts returns 200 on Success`() = withApp {
        coEvery { podcastService.getPodcasts(any(), any()) } returns
            ExtractionResult.Success(testPodcastResponse())
        val response = client.get("/podcasts?url=https://youtube.com/@test")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET podcasts returns 422 on Failure`() = withApp {
        coEvery { podcastService.getPodcasts(any(), any()) } returns
            ExtractionResult.Failure("error")
        val response = client.get("/podcasts?url=https://youtube.com/@test")
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET podcasts returns 400 on BadRequest`() = withApp {
        coEvery { podcastService.getPodcasts(any(), any()) } returns
            ExtractionResult.BadRequest("bad")
        val response = client.get("/podcasts?url=https://youtube.com/@test")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET podcast episodes without url returns 400`() = withApp {
        val response = client.get("/podcasts/episodes")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET podcast episodes returns 200 on Success`() = withApp {
        coEvery { podcastService.getPodcastEpisodes(any(), any()) } returns
            ExtractionResult.Success(testPodcastEpisodesResponse())
        val response = client.get("/podcasts/episodes?url=https://youtube.com/playlist?list=test")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
