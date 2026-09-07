package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionPurpose
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrPlaybackSeekRouteTest {
    @Test
    fun `same formats seek repositions without extracting again`() = testApplication {
        val store = mockk<SabrSessionStore>(relaxed = true)
        val holder = holder()
        every { store.lookupByToken("session-token") } returns holder
        installApp(store)

        val response = client.post("/sabr/playback/session-token/seek") {
            contentType(ContentType.Application.Json)
            setBody("""{"playerTimeMs":90000,"videoItag":136,"audioItag":140}""")
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals(1L, holder.activeGeneration())
        coVerify(exactly = 0) { store.fetchInfo(any(), any(), any()) }
        verify(exactly = 1) { store.startPump(holder) }
    }

    @Test
    fun `format change keeps extraction path`() = testApplication {
        val store = mockk<SabrSessionStore>(relaxed = true)
        every { store.lookupByToken("session-token") } returns holder()
        coEvery { store.fetchInfo("video", 90_000L, cachedFirst = true) } returns null
        installApp(store)

        val response = client.post("/sabr/playback/session-token/seek") {
            contentType(ContentType.Application.Json)
            setBody("""{"playerTimeMs":90000,"videoItag":137,"audioItag":140}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        coVerify(exactly = 1) { store.fetchInfo("video", 90_000L, cachedFirst = true) }
    }

    private fun ApplicationTestBuilder.installApp(store: SabrSessionStore): Unit = application {
        install(ContentNegotiation) { json() }
        val handler = SabrPlaybackHandler(store, mockk<StreamService>(), null, null, null)
        routing {
            post("/sabr/playback/{sessionId}/seek") {
                handler.seek(call, call.parameters["sessionId"].orEmpty())
            }
        }
    }

    private fun holder(): SabrSessionHolder {
        val audio = format(140, true)
        val video = format(136, false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.getCachedSegment(any()) } returns null
        every { session.isBeyondEnd(any()) } returns false
        every { state.getSegmentNumberAtOrAfterTimeMs(any(), 90_000L) } returns 10
        every { state.getSegmentStartMs(any(), 10) } returns 90_000L
        every { state.getMinBufferedEndMs() } returns 10_000L
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            key = SabrSessionKey("video", "user", 140, null, 136, 0L, SabrSessionPurpose.PLAYBACK),
            lastRequestAt = Instant.EPOCH,
        )
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat =
        mockk<YoutubeSabrFormat>(relaxed = true) {
            every { this@mockk.itag } returns itag
            every { this@mockk.isAudio } returns isAudio
            every { audioTrackId } returns null
            every { mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        }
}
