package dev.typetype.server

import dev.typetype.server.models.BulletCommentItem
import dev.typetype.server.models.BulletCommentsPageResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.routes.bulletCommentRoutes
import dev.typetype.server.services.BulletCommentService
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
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BulletCommentRoutesTest {

    private val bulletCommentService: BulletCommentService = mockk()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { bulletCommentRoutes(bulletCommentService) }
        }
        block()
    }

    private fun testBulletCommentItem() = BulletCommentItem(
        text = "hello",
        argbColor = -1,
        position = "REGULAR",
        relativeFontSize = 1.0,
        durationMs = 3000L,
        isLive = false,
    )

    @Test
    fun `GET bullet-comments without url returns 400`() = withApp {
        assertEquals(HttpStatusCode.BadRequest, client.get("/bullet-comments").status)
    }

    @Test
    fun `GET bullet-comments returns 200 on Success`() = withApp {
        coEvery { bulletCommentService.getBulletComments(any(), any()) } returns
            ExtractionResult.Success(BulletCommentsPageResponse(emptyList(), null))
        assertEquals(HttpStatusCode.OK, client.get("/bullet-comments?url=https://www.nicovideo.jp/watch/sm9").status)
    }

    @Test
    fun `GET bullet-comments returns 422 on Failure`() = withApp {
        coEvery { bulletCommentService.getBulletComments(any(), any()) } returns
            ExtractionResult.Failure("failed")
        assertEquals(HttpStatusCode.UnprocessableEntity, client.get("/bullet-comments?url=https://www.nicovideo.jp/watch/sm9").status)
    }

    @Test
    fun `GET bullet-comments returns 400 on BadRequest`() = withApp {
        coEvery { bulletCommentService.getBulletComments(any(), any()) } returns
            ExtractionResult.BadRequest("bad cursor")
        assertEquals(HttpStatusCode.BadRequest, client.get("/bullet-comments?url=https://www.nicovideo.jp/watch/sm9&nextpage=bad").status)
    }

    @Test
    fun `GET bullet-comments serializes all fields`() = withApp {
        val item = testBulletCommentItem()
        coEvery { bulletCommentService.getBulletComments(any(), any()) } returns
            ExtractionResult.Success(BulletCommentsPageResponse(listOf(item), null))
        val body = client.get("/bullet-comments?url=https://www.nicovideo.jp/watch/sm9").bodyAsText()
        assertTrue(body.contains("\"text\":\"hello\""))
        assertTrue(body.contains("\"argbColor\":-1"))
        assertTrue(body.contains("\"position\":\"REGULAR\""))
        assertTrue(body.contains("\"relativeFontSize\":1.0"))
        assertTrue(body.contains("\"durationMs\":3000"))
        assertTrue(body.contains("\"isLive\":false"))
    }

    @Test
    fun `GET bullet-comments serializes nextpage cursor`() = withApp {
        coEvery { bulletCommentService.getBulletComments(any(), any()) } returns
            ExtractionResult.Success(BulletCommentsPageResponse(emptyList(), "cursor-abc"))
        val body = client.get("/bullet-comments?url=https://www.nicovideo.jp/watch/sm9").bodyAsText()
        assertTrue(body.contains("\"nextpage\":\"cursor-abc\""))
    }
}
