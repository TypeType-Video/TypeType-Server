package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.FocusAllowOnceChallengeResponse
import dev.typetype.server.models.FocusAllowOnceVerifyResponse
import dev.typetype.server.models.FocusSettingsItem
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.routes.focusRoutes
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.FocusAllowOnceChallengeResult
import dev.typetype.server.services.FocusAllowOnceVerifyResult
import dev.typetype.server.services.FocusProfile
import dev.typetype.server.services.FocusService
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FocusAllowOnceRoutesTest {
    private val focusService: FocusService = mockk()
    private val streamService: StreamService = mockk()
    private val authService = AuthService.fixed(TEST_USER_ID)

    @Test
    fun `challenge and verify routes map statuses and playback allows only granted video`() = testApplication {
        coEvery { focusService.get(TEST_USER_ID, "Asia/Shanghai") } returns FocusSettingsItem(
            enabled = true,
            entertainmentGroupId = "g1",
            studyGroupId = null,
            schedule = "always",
            timezone = null,
            transitionDays = 60,
            archiveDays = 7,
            strictBlocking = true,
            grayscaleThumbnails = false,
            startedAt = 1L,
            disableAt = null,
            entertainmentShare = 1.0,
            active = true,
            allowOnceRemainingToday = 2,
            allowOnceDailyLimit = 3,
        )
        coEvery {
            focusService.createAllowOnceChallenge(TEST_USER_ID, "https://www.youtube.com/watch?v=vidAllow001", "Asia/Shanghai")
        } returns FocusAllowOnceChallengeResult.Success(
            FocusAllowOnceChallengeResponse("ch-1", "K7p3Mx92Qw4Nr8VlCj5Ty6Ba", 999L, 2, 3),
        )
        coEvery {
            focusService.createAllowOnceChallenge(TEST_USER_ID, "https://www.youtube.com/watch?v=vidExhaust1", null)
        } returns FocusAllowOnceChallengeResult.Exhausted
        coEvery {
            focusService.verifyAllowOnceChallenge(TEST_USER_ID, "ch-1", "https://www.youtube.com/watch?v=vidAllow001", "WRONG", null)
        } returns FocusAllowOnceVerifyResult.InvalidChallenge
        coEvery {
            focusService.verifyAllowOnceChallenge(TEST_USER_ID, "ch-1", "https://www.youtube.com/watch?v=vidAllow001", "K7p3Mx92Qw4Nr8VlCj5Ty6Ba", "Asia/Shanghai")
        } returns FocusAllowOnceVerifyResult.Success(
            FocusAllowOnceVerifyResponse(remainingToday = 1, dailyLimit = 3, expiresAt = 2000L),
        )
        coEvery { focusService.profileFor(TEST_USER_ID) } returns FocusProfile(
            seed = TEST_USER_ID,
            entertainmentChannels = setOf("https://youtube.com/channel/UCfun"),
            entertainmentShare = 1.0,
            archiveDays = 7,
            archiveCutoffMs = 0L,
            strictBlocking = true,
            temporaryAllowedVideos = setOf("youtube:video:vidAllow001"),
        )
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(sampleStream())

        application {
            install(ContentNegotiation) { json() }
            routing {
                focusRoutes(focusService, authService)
                streamRoutes(streamService, authService = authService, focusService = focusService)
            }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.post("/focus/allow-once/challenge").status)

        val focusResp = client.get("/focus?timezone=Asia/Shanghai") { header(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, focusResp.status)
        assertTrue(focusResp.bodyAsText().contains("\"allowOnceRemainingToday\":2"))

        val challengeResp = client.post("/focus/allow-once/challenge") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"videoUrl":"https://www.youtube.com/watch?v=vidAllow001","timezone":"Asia/Shanghai"}""")
        }
        assertEquals(HttpStatusCode.OK, challengeResp.status)
        val challengeBody = challengeResp.bodyAsText()
        assertTrue(challengeBody.contains("\"challengeId\":\"ch-1\""))
        assertTrue(challengeBody.contains("\"challengeText\":\"K7p3Mx92Qw4Nr8VlCj5Ty6Ba\""))

        val exhaustedResp = client.post("/focus/allow-once/challenge") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"videoUrl":"https://www.youtube.com/watch?v=vidExhaust1"}""")
        }
        assertEquals(HttpStatusCode.Conflict, exhaustedResp.status)
        assertTrue(exhaustedResp.bodyAsText().contains("focus_allow_once_exhausted"))

        val invalidVerify = client.post("/focus/allow-once/verify") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"challengeId":"ch-1","videoUrl":"https://www.youtube.com/watch?v=vidAllow001","code":"WRONG"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidVerify.status)
        assertTrue(invalidVerify.bodyAsText().contains("focus_invalid_captcha"))

        val okVerify = client.post("/focus/allow-once/verify") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"challengeId":"ch-1","videoUrl":"https://www.youtube.com/watch?v=vidAllow001","code":"K7p3Mx92Qw4Nr8VlCj5Ty6Ba","timezone":"Asia/Shanghai"}""")
        }
        assertEquals(HttpStatusCode.OK, okVerify.status)
        assertTrue(okVerify.bodyAsText().contains("\"remainingToday\":1"))

        val allowedPlayback = client.get("/streams/youtube/sabr?url=https://www.youtube.com/watch?v=vidAllow001") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, allowedPlayback.status)

        val blockedOther = client.get("/streams/youtube/sabr?url=https://www.youtube.com/watch?v=vidOther999") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.Forbidden, blockedOther.status)
        assertTrue(blockedOther.bodyAsText().contains("focus_channel_blocked"))
    }

    private fun sampleStream(): StreamResponse = testStreamResponse(
        videoOnlyStreams = listOf(testVideoStream().copy(deliveryMethod = "sabr")),
        audioStreams = listOf(testAudioStream(deliveryMethod = "sabr")),
    ).copy(
        title = "Allowed once video",
        uploaderName = "Other Channel",
        uploaderUrl = "https://www.youtube.com/channel/UCother",
    )
}
