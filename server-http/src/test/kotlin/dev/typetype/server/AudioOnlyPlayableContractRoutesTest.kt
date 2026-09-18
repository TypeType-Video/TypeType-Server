package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.routes.audioOnlyContractRoutes
import dev.typetype.server.routes.audioOnlySourceRoutes
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.ProxyService
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class AudioOnlyPlayableContractRoutesTest {
    private val streamService: StreamService = mockk()
    private val proxyService: ProxyService = mockk()
    private val tokenService = AudioOnlyMediaTokenService("test-secret")
    private val hlsTokenService = PublicHlsManifestTokenService("test-secret")

    @Test
    fun `GET audio-only skips progressive streams that cannot be proxied`() = testApplication {
        val blocked = testAudioStream(url = BLOCKED_AUDIO_URL, bitrate = 160, itag = 251)
        val playable = testAudioStream(url = PLAYABLE_AUDIO_URL, bitrate = 128, itag = 140)
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(blocked, playable))
        )
        coEvery { proxyService.pipe(BLOCKED_AUDIO_URL, any(), any()) } returns ExtractionResult.Failure("Upstream returned 403")
        coEvery { proxyService.pipe(PLAYABLE_AUDIO_URL, any(), any()) } answers { playableAudioResponse() }
        installFullApp()

        val contract = client.get("/streams/audio-only?url=https://youtube.com/watch?v=test")
        val source = client.get(contract.bodyAsText().extractSrc()) { header(HttpHeaders.Range, "bytes=0-") }

        assertEquals(HttpStatusCode.OK, contract.status)
        assertTrue(contract.bodyAsText().contains("\"kind\":\"progressive\""))
        assertEquals(HttpStatusCode.PartialContent, source.status)
        assertEquals("audio/mp4", source.headers[HttpHeaders.ContentType]?.substringBefore(";"))
    }

    @Test
    fun `GET audio-only falls back to signed HLS when progressive streams cannot be proxied`() = testApplication {
        val blocked = testAudioStream(url = BLOCKED_AUDIO_URL, bitrate = 160, itag = 251)
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(blocked), hlsUrl = HLS_URL)
        )
        coEvery { proxyService.pipe(BLOCKED_AUDIO_URL, any(), any()) } returns ExtractionResult.Failure("Upstream returned 403")
        installFullApp()

        val response = client.get("/streams/audio-only?url=https://youtube.com/watch?v=test")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"kind\":\"hls\""))
        assertTrue(body.contains("\"mimeType\":\"application/vnd.apple.mpegurl\""))
        assertTrue(body.contains("\"src\":\"/streams/hls-manifest?token="))
        assertFalse(body.contains("manifest.googlevideo.com"))
    }

    @Test
    fun `GET audio-only does not probe Bilibili progressive streams`() = testApplication {
        val audio = testAudioStream(url = BILIBILI_AUDIO_URL, bitrate = 128, itag = 30232)
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(audio))
        )
        installFullApp()

        val response = client.get("/streams/audio-only?url=https://www.bilibili.com/video/BV1test")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"kind\":\"progressive\""))
        coVerify(exactly = 0) { proxyService.pipe(any(), any(), any()) }
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.installFullApp(): Unit = application {
        install(ContentNegotiation) { json() }
        routing {
            audioOnlyContractRoutes(streamService, tokenService, publicHlsManifestTokenService = hlsTokenService, proxyService = proxyService)
            audioOnlySourceRoutes(streamService, proxyService, tokenService)
        }
    }

    private fun playableAudioResponse(): ExtractionResult.Success<ProxyResponse> = ExtractionResult.Success(
        ProxyResponse(
            status = 206,
            contentType = "audio/mp4",
            contentLength = 4,
            contentRange = "bytes 0-3/4",
            acceptRanges = "bytes",
            stream = ByteArrayInputStream(byteArrayOf(0, 1, 2, 3)),
            close = {},
        )
    )

    private fun String.extractSrc(): String = Regex("\"src\":\"([^\"]+)\"").find(this)!!.groupValues[1]

    private companion object {
        const val HLS_URL = "https://manifest.googlevideo.com/api/manifest/hls/test"
        const val BLOCKED_AUDIO_URL = "https://example.googlevideo.com/blocked-audio"
        const val PLAYABLE_AUDIO_URL = "https://example.googlevideo.com/playable-audio"
        const val BILIBILI_AUDIO_URL = "https://upos-hz-mirrorakam.akamaized.net/audio.m4s"
    }
}
