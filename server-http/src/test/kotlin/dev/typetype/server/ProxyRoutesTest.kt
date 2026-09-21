package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.routes.proxyRoutes
import dev.typetype.server.services.ProxyService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.excludeContentType
import io.ktor.server.plugins.compression.gzip
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ProxyRoutesTest {

    private val proxyService: ProxyService = mockk()

    @Test
    fun `GET proxy without url returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { proxyRoutes(proxyService) }
        }
        val response = client.get("/proxy")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET proxy does not gzip media responses`() = testApplication {
        coEvery { proxyService.pipe(any(), any(), any()) } returns ExtractionResult.Success(
            ProxyResponse(
                status = 206,
                contentType = "audio/webm",
                contentLength = 2048,
                contentRange = "bytes 0-2047/2048",
                acceptRanges = "bytes",
                stream = ByteArrayInputStream(ByteArray(2048) { 1 }),
                close = {},
            )
        )
        application {
            install(ContentNegotiation) { json() }
            install(Compression) {
                gzip {
                    excludeContentType(ContentType.parse("application/vnd.apple.mpegurl"))
                    excludeContentType(ContentType.Image.Any)
                    excludeContentType(ContentType.Video.Any)
                    excludeContentType(ContentType.Audio.Any)
                    excludeContentType(ContentType.Application.OctetStream)
                }
            }
            routing { proxyRoutes(proxyService) }
        }
        val response = client.get("/proxy?url=https://example.com/stream") {
            header("Accept-Encoding", "gzip")
            header("Range", "bytes=0-2047")
        }
        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertNull(response.headers["Content-Encoding"])
    }

    @Test
    fun `GET proxy does not gzip image responses`() = testApplication {
        coEvery { proxyService.pipe(any(), any(), any()) } returns ExtractionResult.Success(
            ProxyResponse(
                status = 200,
                contentType = "image/jpeg",
                contentLength = 2048,
                contentRange = null,
                acceptRanges = null,
                stream = ByteArrayInputStream(ByteArray(2048) { 1 }),
                close = {},
            )
        )
        application {
            install(ContentNegotiation) { json() }
            install(Compression) {
                gzip {
                    excludeContentType(ContentType.parse("application/vnd.apple.mpegurl"))
                    excludeContentType(ContentType.Image.Any)
                    excludeContentType(ContentType.Video.Any)
                    excludeContentType(ContentType.Audio.Any)
                    excludeContentType(ContentType.Application.OctetStream)
                }
            }
            routing { proxyRoutes(proxyService) }
        }
        val response = client.get("/proxy?url=https://example.com/thumb.jpg") {
            header("Accept-Encoding", "gzip")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.headers["Content-Encoding"])
    }

    @Test
    fun `GET proxy forwards domand bid`() = testApplication {
        coEvery { proxyService.pipe(any(), any(), any()) } returns ExtractionResult.Success(
            ProxyResponse(200, "image/jpeg", 1, null, null, ByteArrayInputStream(byteArrayOf(1)), {})
        )
        application {
            install(ContentNegotiation) { json() }
            routing { proxyRoutes(proxyService) }
        }
        client.get("/proxy?url=https://example.com/thumb.jpg&domand_bid=abc123")
        coVerify { proxyService.pipe("https://example.com/thumb.jpg", null, "abc123") }
    }
}
