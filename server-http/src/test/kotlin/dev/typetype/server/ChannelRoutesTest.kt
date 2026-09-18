package dev.typetype.server

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.routes.channelRoutes
import dev.typetype.server.services.ChannelService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
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
import org.junit.jupiter.api.Test

class ChannelRoutesTest {

    private val channelService: ChannelService = mockk()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { channelRoutes(channelService) }
        }
        block()
    }

    private fun testChannelResponse() = ChannelResponse(
        name = "Test Channel",
        description = "",
        avatarUrl = "",
        bannerUrl = "",
        subscriberCount = 0L,
        isVerified = false,
        videos = emptyList(),
        nextpage = null,
    )

    @Test
    fun `GET channel without url returns 400`() = withApp {
        val response = client.get("/channel")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET channel returns 200 on Success`() = withApp {
        coEvery { channelService.getChannel(any(), any(), any()) } returns
            ExtractionResult.Success(testChannelResponse())
        val response = client.get("/channel?url=https://youtube.com/channel/test")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET channel returns 422 on Failure`() = withApp {
        coEvery { channelService.getChannel(any(), any(), any()) } returns
            ExtractionResult.Failure("error")
        val response = client.get("/channel?url=https://youtube.com/channel/test")
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET channel returns 400 on BadRequest`() = withApp {
        coEvery { channelService.getChannel(any(), any(), any()) } returns
            ExtractionResult.BadRequest("bad")
        val response = client.get("/channel?url=https://youtube.com/channel/test")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET channel keeps encoded search url`() = withApp {
        coEvery { channelService.getChannel(any(), any(), any()) } returns
            ExtractionResult.Success(testChannelResponse())
        val response = client.get("/channel?url=https%3A%2F%2Fwww.youtube.com%2F%40test%2Fsearch%3Fquery%3Dtyping")

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify { channelService.getChannel("https://www.youtube.com/@test/search?query=typing", null, null) }
    }

    @Test
    fun `POST channel page accepts long nextpage in body`() = withApp {
        val nextpage = "x".repeat(9_000)
        coEvery { channelService.getChannel(any(), any(), any()) } returns
            ExtractionResult.Success(testChannelResponse())

        val response = client.post("/channel/page") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"url":"https://youtube.com/channel/test","nextpage":"$nextpage","sort":"latest"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify { channelService.getChannel("https://youtube.com/channel/test", nextpage, "latest") }
    }

    @Test
    fun `POST channel page without url returns 400`() = withApp {
        val response = client.post("/channel/page") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"nextpage":"cursor"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
