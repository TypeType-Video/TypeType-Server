package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.routes.audioOnlySourceRoutes
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.ProxyService
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class AudioOnlySourceRoutesTest {
    private val streamService: StreamService = mockk()
    private val proxyService: ProxyService = mockk()
    private val tokenService = AudioOnlyMediaTokenService("test-secret")

    @Test
    fun `GET audio-only source streams selected audio with Range`() = testApplication {
        val selected = testAudioStream(url = "https://example.googlevideo.com/audio-ja", audioLocale = "ja")
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(testAudioStream(audioLocale = "en"), selected))
        )
        coEvery { proxyService.pipe(any(), any(), any()) } returns ExtractionResult.Success(
            ProxyResponse(
                status = 206,
                contentType = "audio/mp4",
                contentLength = 4,
                contentRange = "bytes 0-3/5000000",
                acceptRanges = "bytes",
                stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
                close = {},
            )
        )
        installSourceApp()
        val token = tokenService.createToken(null, "https://youtube.com/watch?v=test", false, "ja", 140, null)

        val response = client.get("/streams/audio-only/source?token=$token") {
            header(HttpHeaders.Range, "bytes=0-3")
        }

        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertEquals("audio/mp4", response.headers[HttpHeaders.ContentType])
        assertEquals("bytes 0-3/5000000", response.headers[HttpHeaders.ContentRange])
        assertEquals("\u0001\u0002\u0003\u0004", response.bodyAsText())
        coVerify { proxyService.pipe("https://example.googlevideo.com/audio-ja", "bytes=0-3", null) }
    }

    @Test
    fun `GET audio-only source without Range streams the complete media`() = testApplication {
        val selected = testAudioStream(url = "https://example.googlevideo.com/audio-en", audioLocale = "en")
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(selected))
        )
        coEvery { proxyService.pipe(any(), any(), any()) } returns ExtractionResult.Success(
            ProxyResponse(
                status = 200,
                contentType = "video/mp4",
                contentLength = 4,
                contentRange = null,
                acceptRanges = "bytes",
                stream = ByteArrayInputStream(byteArrayOf(5, 6, 7, 8)),
                close = {},
            )
        )
        installSourceApp()
        val token = tokenService.createToken(null, "https://youtube.com/watch?v=test", false, "en", 140, null)

        val response = client.get("/streams/audio-only/source?token=$token")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("audio/mp4", response.headers[HttpHeaders.ContentType])
        coVerify { proxyService.pipe("https://example.googlevideo.com/audio-en", null, null) }
    }

    @Test
    fun `GET audio-only source rejects invalid token`() = testApplication {
        installSourceApp()

        val response = client.get("/streams/audio-only/source?token=invalid")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `HEAD audio-only source returns media headers for valid token`() = testApplication {
        val selected = testAudioStream(url = "https://example.googlevideo.com/audio-en", audioLocale = "en")
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(selected))
        )
        installSourceApp()
        val token = tokenService.createToken(null, "https://youtube.com/watch?v=test", false, "en", 140, null)

        val response = client.head("/streams/audio-only/source?token=$token")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("audio/mp4", response.headers[HttpHeaders.ContentType])
        assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
        assertEquals("5000000", response.headers[HttpHeaders.ContentLength])
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `HEAD audio-only source supports Range probe`() = testApplication {
        val selected = testAudioStream(url = "https://example.googlevideo.com/audio-en", audioLocale = "en")
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(selected))
        )
        installSourceApp()
        val token = tokenService.createToken(null, "https://youtube.com/watch?v=test", false, "en", 140, null)

        val response = client.head("/streams/audio-only/source?token=$token") {
            header(HttpHeaders.Range, "bytes=0-3")
        }

        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertEquals("audio/mp4", response.headers[HttpHeaders.ContentType])
        assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
        assertEquals("bytes 0-3/5000000", response.headers[HttpHeaders.ContentRange])
        assertEquals("4", response.headers[HttpHeaders.ContentLength])
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `HEAD audio-only source probes missing content length`() = testApplication {
        val selected = testAudioStream(
            url = "https://example.googlevideo.com/audio-en",
            audioLocale = "en",
            contentLength = 0,
        )
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(selected))
        )
        coEvery { proxyService.pipe(any(), "bytes=0-0", any()) } returns ExtractionResult.Success(
            ProxyResponse(
                status = 206,
                contentType = "audio/mp4",
                contentLength = 1,
                contentRange = "bytes 0-0/3000000",
                acceptRanges = "bytes",
                stream = ByteArrayInputStream(byteArrayOf(1)),
                close = {},
            )
        )
        installSourceApp()
        val token = tokenService.createToken(null, "https://youtube.com/watch?v=test", false, "en", 140, null)

        val response = client.head("/streams/audio-only/source?token=$token")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("3000000", response.headers[HttpHeaders.ContentLength])
        assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
        coVerify { proxyService.pipe("https://example.googlevideo.com/audio-en", "bytes=0-0", null) }
    }

    @Test
    fun `HEAD audio-only source rejects invalid token`() = testApplication {
        installSourceApp()

        val response = client.head("/streams/audio-only/source?token=invalid")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.installSourceApp(): Unit = application {
        install(ContentNegotiation) { json() }
        routing { audioOnlySourceRoutes(streamService, proxyService, tokenService) }
    }
}
