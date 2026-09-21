package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.routes.nicoVideoProxyRoutes
import dev.typetype.server.services.NicoVideoProxyService
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class NicoVideoProxyRoutesTest {

    private val nicoVideoProxyService: NicoVideoProxyService = mockk()

    @Test
    fun `GET proxy nicovideo without url returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { nicoVideoProxyRoutes(nicoVideoProxyService) }
        }
        val response = client.get("/proxy/nicovideo")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET proxy nicovideo manifest returns 200 with mpegurl content type`() = testApplication {
        coEvery { nicoVideoProxyService.fetchManifest(any()) } returns ExtractionResult.Success(
            ProxyResponse(
                status = 200,
                contentType = "application/vnd.apple.mpegurl",
                contentLength = null,
                contentRange = null,
                acceptRanges = null,
                stream = ByteArrayInputStream("#EXTM3U".toByteArray()),
                close = {},
            )
        )
        application {
            install(ContentNegotiation) { json() }
            routing { nicoVideoProxyRoutes(nicoVideoProxyService) }
        }
        val response = client.get("/proxy/nicovideo?url=https%3A%2F%2Fdelivery.domand.nicovideo.jp%2Fvideo.m3u8")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET proxy nicovideo segment upstream failure returns 422`() = testApplication {
        coEvery { nicoVideoProxyService.fetchSegment(any(), any()) } returns
            ExtractionResult.Failure("Upstream returned 403")
        application {
            install(ContentNegotiation) { json() }
            routing { nicoVideoProxyRoutes(nicoVideoProxyService) }
        }
        val response = client.get("/proxy/nicovideo?url=https%3A%2F%2Fdelivery.domand.nicovideo.jp%2Fseg.ts")
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }
}
