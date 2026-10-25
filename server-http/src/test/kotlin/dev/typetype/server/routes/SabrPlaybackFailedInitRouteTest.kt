package dev.typetype.server.routes

import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.services.SabrFailureKind
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionPurpose
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SabrPlaybackFailedInitRouteTest {
    @Test
    fun `init of a refused session answers 503 with retry after instead of a 202 loop`() = testApplication {
        val store = mockk<SabrSessionStore>(relaxed = true)
        val holder = holder()
        holder.failTerminal("SABR recoverable failure: refused", SabrFailureKind.UPSTREAM_REFUSED)
        every { store.lookupByToken("session-token") } returns holder
        application {
            install(ContentNegotiation) { json() }
            val handler = SabrPlaybackHandler(store, mockk<StreamService>(), null, null, null)
            routing {
                get("/sabr/playback/{sessionId}/{itag}/init") {
                    handler.segment(call, call.parameters["sessionId"].orEmpty(), isInit = true, seq = 0)
                }
            }
        }

        val response = client.get("/sabr/playback/session-token/136/init")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("30", response.headers[HttpHeaders.RetryAfter])
        assertTrue(response.bodyAsText().contains("\"code\":\"sabr_upstream_refused\""))
        coVerify(exactly = 0) { store.fetchInitializationData(any(), any()) }
    }

    private fun holder(): SabrSessionHolder = SabrSessionHolder(
        session = mockk<YoutubeSabrSession>(relaxed = true),
        info = mockk<YoutubeSabrInfo>(),
        audioFormat = format(140, true),
        videoFormat = format(136, false),
        sessionToken = "session-token",
        key = SabrSessionKey("video", "user", 140, null, 136, 0L, SabrSessionPurpose.PLAYBACK),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat =
        mockk<YoutubeSabrFormat>(relaxed = true) {
            every { this@mockk.itag } returns itag
            every { this@mockk.isAudio } returns isAudio
            every { audioTrackId } returns null
        }
}
