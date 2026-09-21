package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.routes.audioOnlySourceRoutes
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.ProxyService
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.request.header
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

class AudioOnlySourceOpenRangeRoutesTest {
    private val streamService: StreamService = mockk()
    private val proxyService: ProxyService = mockk()
    private val tokenService = AudioOnlyMediaTokenService("test-secret")

    @Test
    fun `GET audio-only source with open Range requests bounded initial media`() = testApplication {
        val selected = testAudioStream(url = "https://example.googlevideo.com/audio-en", audioLocale = "en")
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(selected))
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
        val token = tokenService.createToken(null, "https://youtube.com/watch?v=test", false, "en", 140, null)

        val response = client.get("/streams/audio-only/source?token=$token") {
            header(HttpHeaders.Range, "bytes=0-")
        }

        assertEquals(HttpStatusCode.PartialContent, response.status)
        coVerify { proxyService.pipe("https://example.googlevideo.com/audio-en", "bytes=0-1048575", null) }
    }

    @Test
    fun `GET audio-only source completes open Range for small media`() = testApplication {
        val selected = testAudioStream(
            url = "https://example.googlevideo.com/audio-en",
            audioLocale = "en",
            contentLength = 3_000_000,
        )
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(selected))
        )
        coEvery { proxyService.pipe(any(), any(), any()) } returns ExtractionResult.Success(
            ProxyResponse(
                status = 206,
                contentType = "audio/mp4",
                contentLength = 4,
                contentRange = "bytes 200000-2999999/3000000",
                acceptRanges = "bytes",
                stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
                close = {},
            )
        )
        installSourceApp()
        val token = tokenService.createToken(null, "https://youtube.com/watch?v=test", false, "en", 140, null)

        val response = client.get("/streams/audio-only/source?token=$token") {
            header(HttpHeaders.Range, "bytes=200000-")
        }

        assertEquals(HttpStatusCode.PartialContent, response.status)
        coVerify { proxyService.pipe("https://example.googlevideo.com/audio-en", "bytes=200000-2999999", null) }
    }

    @Test
    fun `GET audio-only source probes missing length before completing open Range`() = testApplication {
        val selected = testAudioStream(
            url = "https://example.googlevideo.com/audio-en",
            audioLocale = "en",
            contentLength = 0,
        )
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(selected))
        )
        coEvery { proxyService.pipe(any(), "bytes=0-0", any()) } returns ExtractionResult.Success(
            proxyResponse(contentRange = "bytes 0-0/3000000")
        )
        coEvery { proxyService.pipe(any(), "bytes=200000-2999999", any()) } returns ExtractionResult.Success(
            proxyResponse(contentRange = "bytes 200000-2999999/3000000")
        )
        installSourceApp()
        val token = tokenService.createToken(null, "https://youtube.com/watch?v=test", false, "en", 140, null)

        val response = client.get("/streams/audio-only/source?token=$token") {
            header(HttpHeaders.Range, "bytes=200000-")
        }

        assertEquals(HttpStatusCode.PartialContent, response.status)
        coVerify { proxyService.pipe("https://example.googlevideo.com/audio-en", "bytes=0-0", null) }
        coVerify { proxyService.pipe("https://example.googlevideo.com/audio-en", "bytes=200000-2999999", null) }
    }

    private fun proxyResponse(contentRange: String): ProxyResponse = ProxyResponse(
        status = 206,
        contentType = "audio/mp4",
        contentLength = 1,
        contentRange = contentRange,
        acceptRanges = "bytes",
        stream = ByteArrayInputStream(byteArrayOf(1)),
        close = {},
    )

    private fun io.ktor.server.testing.ApplicationTestBuilder.installSourceApp(): Unit = application {
        install(ContentNegotiation) { json() }
        routing { audioOnlySourceRoutes(streamService, proxyService, tokenService) }
    }
}
