package dev.typetype.server.routes

import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.services.SabrFailureKind
import dev.typetype.server.services.SabrPreparedInfo
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionPurpose
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SabrPlaybackBornTerminalRouteTest {
    private val audio = format(140, isAudio = true)
    private val video = format(136, isAudio = false)
    private val info = mockk<YoutubeSabrInfo>(relaxed = true) {
        every { formats } returns listOf(audio, video)
        every { findFormatByItag(136) } returns video
    }
    private val holder = SabrSessionHolder(
        session = mockk<YoutubeSabrSession>(relaxed = true),
        info = info,
        audioFormat = audio,
        videoFormat = video,
        sessionToken = "session-token",
        key = SabrSessionKey("video-id", "video-id", 140, null, 136, 0L, SabrSessionPurpose.PLAYBACK),
        lastRequestAt = Instant.EPOCH,
    )
    private val store = mockk<SabrSessionStore>(relaxed = true) {
        coEvery { fetchInfo("video-id", 0L, cachedFirst = true) } returns SabrPreparedInfo(info, null)
        every { getOrCreate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns holder
    }

    @Test
    fun `a session whose initialization is missing answers 422 and is released`() = testApplication {
        coEvery { store.fetchInitializationData(holder, any()) } returns null
        installApp()

        val response = create()

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"code\":\"sabr_content_unavailable\""), body)
        assertNull(response.headers[HttpHeaders.RetryAfter])
        verify(exactly = 1) { store.release(holder) }
    }

    @Test
    fun `a session refused by YouTube at birth answers 503 with retry after and is released`() = testApplication {
        coEvery { store.fetchInitializationData(holder, any()) } answers {
            holder.failTerminal("refused", SabrFailureKind.UPSTREAM_REFUSED)
            null
        }
        installApp()

        val response = create()

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("30", response.headers[HttpHeaders.RetryAfter])
        assertTrue(response.bodyAsText().contains("\"code\":\"sabr_upstream_refused\""))
        verify(exactly = 1) { store.release(holder) }
    }

    private suspend fun ApplicationTestBuilder.create(): HttpResponse = client.post("/sabr/playback/video-id") {
        contentType(ContentType.Application.Json)
        setBody("""{"videoItag":136,"audioItag":140}""")
    }

    private fun ApplicationTestBuilder.installApp(): Unit = application {
        install(ContentNegotiation) { json() }
        val handler = SabrPlaybackHandler(store, mockk<StreamService>(), null, null, null)
        routing {
            post("/sabr/playback/{videoId}") { handler.create(call, call.parameters["videoId"].orEmpty()) }
        }
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat = mockk(relaxed = true) {
        every { this@mockk.itag } returns itag
        every { this@mockk.isAudio } returns isAudio
        every { isVideo } returns !isAudio
        every { audioTrackId } returns null
        every { mimeType } returns if (isAudio) "audio/mp4; codecs=\"mp4a.40.2\"" else "video/mp4; codecs=\"avc1.4d401f\""
        every { bitrate } returns if (isAudio) 128_000 else 2_000_000
    }
}
