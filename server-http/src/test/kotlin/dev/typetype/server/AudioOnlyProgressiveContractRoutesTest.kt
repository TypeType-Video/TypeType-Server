package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.routes.audioOnlyContractRoutes
import dev.typetype.server.routes.audioOnlySourceRoutes
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.ProxyService
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
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class AudioOnlyProgressiveContractRoutesTest {
    private val streamService: StreamService = mockk()
    private val proxyService: ProxyService = mockk()
    private val tokenService = AudioOnlyMediaTokenService("test-secret")
    private val hlsTokenService = PublicHlsManifestTokenService("test-secret")

    @Test
    fun `GET audio-only returns signed HLS fallback for HLS manifest audio streams`() = testApplication {
        val hlsAudio = testAudioStream(url = "https://manifest.googlevideo.com/api/manifest/hls/test", audioLocale = "en")
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(hlsAudio))
        )
        installContractApp()

        val response = client.get("/streams/audio-only?url=https://youtube.com/watch?v=test")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"src\":\"/streams/hls-manifest?token="))
        assertTrue(body.contains("\"kind\":\"hls\""))
        assertTrue(body.contains("\"mimeType\":\"application/vnd.apple.mpegurl\""))
        assertFalse(body.contains("manifest.googlevideo.com"))
    }

    @Test
    fun `GET audio-only returns SABR HLS manifest when only SABR audio is available`() = testApplication {
        val opusSabrAudio = testAudioStream(
            url = "",
            codec = "opus",
            bitrate = 160,
            deliveryMethod = "sabr",
            manifestUrl = "/sabr/manifest/test-id",
            sabrSessionUrl = "/sabr/session/test-id?audioItag=251",
        ).copy(mimeType = "audio/webm", format = "WebM Opus", itag = 251)
        val sabrAudio = testAudioStream(
            url = "",
            bitrate = 128,
            deliveryMethod = "sabr",
            sabrSessionUrl = "/sabr/session/test-id?audioItag=140",
        )
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(opusSabrAudio, sabrAudio))
        )
        installContractApp()

        val response = client.get("/streams/audio-only?url=https://youtube.com/watch?v=test")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"src\":\"/sabr/manifest/test-id?audioOnly=true&format=hls&audioToken="))
        assertTrue(body.contains("\"kind\":\"hls\""))
        assertTrue(body.contains("\"mimeType\":\"application/vnd.apple.mpegurl\""))
        assertTrue(body.contains("\"bitrate\":128"))
        assertFalse(body.contains("\"codec\":\"opus\""))
        assertTrue(body.contains("/sabr/manifest"))
    }

    @Test
    fun `GET audio-only does not require complete SABR body preflight for HLS`() = testApplication {
        val sabrAudio = testAudioStream(
            url = "",
            bitrate = 128,
            deliveryMethod = "sabr",
            manifestUrl = "/sabr/manifest/test-id",
            sabrSessionUrl = "/sabr/session/test-id?audioItag=140",
        )
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(sabrAudio))
        )
        installContractApp()

        val response = client.get("/streams/audio-only?url=https://youtube.com/watch?v=test")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"kind\":\"hls\""))
        assertTrue(body.contains("/sabr/manifest/test-id"))
    }

    @Test
    fun `GET audio-only source rejects HLS upstream response`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(testStreamResponse())
        coEvery { proxyService.pipe(any(), any(), any()) } returns ExtractionResult.Success(
            ProxyResponse(
                status = 206,
                contentType = "application/vnd.apple.mpegurl",
                contentLength = null,
                contentRange = null,
                acceptRanges = null,
                stream = ByteArrayInputStream("#EXTM3U".toByteArray()),
                close = {},
            )
        )
        installSourceApp()
        val token = tokenService.createToken(null, "https://youtube.com/watch?v=test", false, "en", 140, null)

        val response = client.get("/streams/audio-only/source?token=$token") {
            header(HttpHeaders.Range, "bytes=0-1023")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("Audio-only source did not return progressive audio"))
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.installContractApp(): Unit = application {
        install(ContentNegotiation) { json() }
        routing {
            audioOnlyContractRoutes(
                streamService,
                tokenService,
                publicHlsManifestTokenService = hlsTokenService,
            )
        }
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.installSourceApp(): Unit = application {
        install(ContentNegotiation) { json() }
        routing { audioOnlySourceRoutes(streamService, proxyService, tokenService) }
    }

}
