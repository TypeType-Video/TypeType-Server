package dev.typetype.server

import dev.typetype.server.models.ChannelPostsResponse
import dev.typetype.server.models.CommunityPostItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.routes.channelPostsRoutes
import dev.typetype.server.services.ChannelPostsService
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

class ChannelPostsRoutesTest {

    private val postsService: ChannelPostsService = mockk()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { channelPostsRoutes(postsService) }
        }
        block()
    }

    private fun response() = ChannelPostsResponse(
        channelName = "Blender",
        channelUrl = "https://www.youtube.com/@BlenderOfficial",
        posts = listOf(
            CommunityPostItem(
                id = "post-1",
                url = "https://www.youtube.com/post/post-1",
                type = "text",
                text = "Hello",
                authorName = "Blender",
                authorUrl = "https://www.youtube.com/@BlenderOfficial",
                authorAvatarUrl = "",
                publishedTime = "3 days ago",
                textualLikeCount = "12",
                textualCommentCount = "",
            ),
        ),
        nextpage = "cursor",
    )

    @Test
    fun `GET channel posts without url returns 400`() = withApp {
        assertEquals(HttpStatusCode.BadRequest, client.get("/channel/posts").status)
    }

    @Test
    fun `GET channel posts returns the page and passes the cursor`() = withApp {
        coEvery { postsService.getPosts(any(), any()) } returns ExtractionResult.Success(response())

        val result = client.get("/channel/posts?url=https://www.youtube.com/@BlenderOfficial&nextpage=abc")

        assertEquals(HttpStatusCode.OK, result.status)
        assertTrue(result.bodyAsText().contains("\"post-1\""))
        coVerify { postsService.getPosts("https://www.youtube.com/@BlenderOfficial", "abc") }
    }

    @Test
    fun `GET channel posts returns 400 for a non YouTube channel`() = withApp {
        coEvery { postsService.getPosts(any(), any()) } returns
            ExtractionResult.BadRequest("Posts are only supported for YouTube channels")

        assertEquals(HttpStatusCode.BadRequest, client.get("/channel/posts?url=https://space.bilibili.com/1").status)
    }

    @Test
    fun `GET channel posts returns 422 on Failure`() = withApp {
        coEvery { postsService.getPosts(any(), any()) } returns ExtractionResult.Failure("error")

        assertEquals(
            HttpStatusCode.UnprocessableEntity,
            client.get("/channel/posts?url=https://www.youtube.com/@BlenderOfficial").status,
        )
    }
}
