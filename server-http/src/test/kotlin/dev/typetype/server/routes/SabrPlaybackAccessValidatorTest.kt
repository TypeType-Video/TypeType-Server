package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.testStreamResponse
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SabrPlaybackAccessValidatorTest {
    @Test
    fun `unrestricted playback does not wait for full stream metadata`() = testApplication {
        val store = mockk<SabrSessionStore>(relaxed = true)
        val streams = mockk<StreamService>()
        coEvery { store.fetchInfo("video-id", 0L, cachedFirst = true) } returns null
        application {
            install(ContentNegotiation) { json() }
            val handler = SabrPlaybackHandler(store, streams, null, null, null)
            routing {
                post("/sabr/playback/{videoId}") {
                    handler.create(call, call.parameters["videoId"].orEmpty())
                }
            }
        }

        val response = client.post("/sabr/playback/video-id")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        coVerify(exactly = 0) { streams.getStreamInfo(any()) }
    }

    @Test
    fun `uses linked YouTube session even when public metadata is accessible`() = runBlocking {
        val authenticated = ExtractionResult.Success(testStreamResponse().copy(title = "Authenticated"))
        val validator = validator(
            publicResult = ExtractionResult.Success(testStreamResponse().copy(title = "Public")),
            authenticatedResult = authenticated,
        )

        assertEquals(authenticated, validator.resolve("user-id", "video-id"))
    }

    @Test
    fun `uses linked YouTube session for age-restricted playback`() = runBlocking {
        val authenticated = ExtractionResult.Success(testStreamResponse())
        val validator = validator(
            publicResult = ExtractionResult.BadRequest("Confirm your age", "age_restricted"),
            authenticatedResult = authenticated,
        )

        assertEquals(authenticated, validator.resolve("user-id", "video-id"))
    }

    @Test
    fun `asks for YouTube connection when restricted playback has no session`() = runBlocking {
        val validator = validator(
            publicResult = ExtractionResult.BadRequest("Sign in", "members_only"),
            authenticatedResult = null,
        )

        assertEquals(
            ExtractionResult.BadRequest("Connect YouTube to access this video", "youtube_session_required"),
            validator.resolve(null, "video-id"),
        )
    }

    @Test
    fun `keeps membership error when linked account lacks access`() = runBlocking {
        val membersOnly = ExtractionResult.BadRequest("Join this channel", "members_only")
        val validator = validator(
            publicResult = ExtractionResult.Success(testStreamResponse().copy(requiresMembership = true)),
            authenticatedResult = membersOnly,
        )

        assertEquals(membersOnly, validator.resolve("user-id", "video-id"))
    }

    private fun validator(
        publicResult: ExtractionResult<StreamResponse>,
        authenticatedResult: ExtractionResult<StreamResponse>?,
    ): SabrPlaybackAccessValidator = SabrPlaybackAccessValidator(
        publicStreamService = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> = publicResult
        },
        youtubeSessionStreamInfo = { _, _ -> authenticatedResult },
    )
}
