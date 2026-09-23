package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PublicHlsManifestTokenResult
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.request.headers
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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StreamRoutesSignedHlsTest {
    private val streamService: StreamService = mockk()
    private val adminSettings = AdminSettingsService()
    private val tokenService = PublicHlsManifestTokenService("test-secret")

    companion object {
        const val MANIFEST_URL = "https://manifest.googlevideo.com/api/manifest/hls/test"

        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `authenticated streams signs public HLS URL when guests are disabled`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(allowGuest = false))
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(publicHlsStream())
        installApp()

        val response = client.get("/streams/niconico?url=https://www.nicovideo.jp/watch/sm9") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"hlsUrl\":\"/streams/hls-manifest?token="))
        assertFalse(body.contains(MANIFEST_URL))
        val token = body.substringAfter("/streams/hls-manifest?token=").substringBefore('"')
        val verified = tokenService.verify(token)
        assertTrue(verified is PublicHlsManifestTokenResult.Valid)
    }

    @Test
    fun `anonymous sabr live response keeps only signed HLS`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            publicHlsStream().copy(
                videoOnlyStreams = listOf(
                    testVideoStream().copy(url = "", deliveryMethod = "sabr", sabrSessionUrl = "/sabr/session/test"),
                ),
                audioStreams = listOf(
                    testAudioStream(url = "", deliveryMethod = "sabr", sabrSessionUrl = "/sabr/session/test"),
                ),
                isLive = true,
                isLiveContent = true,
                hasLiveManifest = true,
            ),
        )
        installApp()

        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=test")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"hlsUrl\":\"/streams/hls-manifest?token="))
        assertTrue(body.contains("\"videoStreams\":[]"))
        assertTrue(body.contains("\"videoOnlyStreams\":[]"))
        assertTrue(body.contains("\"audioStreams\":[]"))
        assertFalse(body.contains("\"deliveryMethod\":\"sabr\""))
        assertFalse(body.contains(MANIFEST_URL))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `anonymous streams still fails when guests are disabled`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(allowGuest = false))
        installApp()

        val response = client.get("/streams/youtube/sabr?url=https://youtube.com/watch?v=test")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.installApp(): Unit = application {
        install(ContentNegotiation) { json() }
        routing {
            streamRoutes(
                streamService,
                AuthService.fixed(TEST_USER_ID),
                adminSettingsService = adminSettings,
                publicHlsManifestTokenService = tokenService,
            )
        }
    }

    private fun publicHlsStream() = testStreamResponse().copy(hlsUrl = MANIFEST_URL)

}
