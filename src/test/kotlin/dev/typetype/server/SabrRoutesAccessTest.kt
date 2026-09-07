package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.routes.sabrRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrRoutesAccessTest {
    private val adminSettings = AdminSettingsService()
    private val streamService: StreamService = mockk(relaxed = true)
    private val sabrSessionStore: SabrSessionStore = mockk(relaxed = true)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `manifest route rejects anonymous when guests are disabled`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(allowGuest = false))
        installSabrApp()

        val response = client.get("/sabr/manifest/dqWhXeGQkgU")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `media segment without session rejects anonymous when guests are disabled`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(allowGuest = false))
        installSabrApp()

        val response = client.get("/sabr/dqWhXeGQkgU/140/segment/14")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `segment without session rejects anonymous when guests are disabled`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(allowGuest = false))
        installSabrApp()

        val response = client.get("/sabr/dqWhXeGQkgU/140/init")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `manifest with explicit session uses that session when guests are disabled`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(allowGuest = false))
        val holder = sabrHolder()
        every { sabrSessionStore.lookupByToken("dqWhXeGQkgU", "session-token") } returns holder
        every { sabrSessionStore.startPump(holder) } returns Unit
        installSabrApp()

        val response = client.get("/sabr/manifest/dqWhXeGQkgU?session=session-token")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("session=session-token"))
    }

    private fun ApplicationTestBuilder.installSabrApp(): Unit = application {
        install(ContentNegotiation) { json() }
        routing {
            sabrRoutes(
                sabrSessionStore,
                streamService,
                AuthService("sabr-test-secret"),
                accessControlService = null,
                adminSettingsService = adminSettings,
                audioOnlyTokenService = null,
            )
        }
    }

    private fun sabrHolder(): SabrSessionHolder {
        val audio = sabrFormat(140, "audio/mp4; codecs=\"mp4a.40.2\"", isAudio = true)
        val video = sabrFormat(137, "video/mp4; codecs=\"avc1.640028\"", isAudio = false)
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns state
        every { state.setActiveTrackTypes(true, true) } returns Unit
        every { state.getEndSegment(audio) } returns 1L
        every { state.getEndSegment(video) } returns 1L
        every { state.getSegmentStartMs(audio, 1) } returns 0L
        every { state.getSegmentEndMs(audio, 1) } returns 1_000L
        every { state.getSegmentStartMs(video, 1) } returns 0L
        every { state.getSegmentEndMs(video, 1) } returns 1_000L
        return SabrSessionHolder(
            session,
            mockk<YoutubeSabrInfo>(),
            audio,
            video,
            "session-token",
            SabrSessionKey("dqWhXeGQkgU", "user", 140, null, 137, 0L),
            Instant.EPOCH,
        )
    }

    private fun sabrFormat(itag: Int, mime: String, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.mimeType } returns mime
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        every { format.width } returns if (isAudio) 0 else 1920
        every { format.height } returns if (isAudio) 0 else 1080
        every { format.approxDurationMs } returns 1_000L
        return format
    }
}
