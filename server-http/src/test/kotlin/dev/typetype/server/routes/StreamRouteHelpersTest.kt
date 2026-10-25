package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.testStreamResponse
import dev.typetype.server.testVideoItem
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class StreamRouteHelpersTest {
    @Test
    fun signedInUnrestrictedStreamsSkipAuthenticatedResolution() = runBlocking {
        var authenticatedCalls = 0
        val public = ExtractionResult.Success(testStreamResponse())
        val result = resolveStreamInfo(
            url = "https://www.youtube.com/watch?v=public-video",
            deliveryMode = StreamDeliveryMode.YoutubeSabr,
            userId = "user-id",
            publicResult = public,
            dependencies = StreamRouteDependencies(
                authService = null,
                accessControlService = null,
                adminSettingsService = null,
                blockedService = null,
                publicHlsManifestTokenService = null,
                providerMediaHandleService = null,
                sabrStreamContractFilter = null,
                youtubeSessionSabrStreamInfo = { _, _ ->
                    authenticatedCalls += 1
                    ExtractionResult.Success(testStreamResponse())
                },
                bilibiliSessionStreamInfo = null,
                youtubeRelatedVideos = null,
            ),
        )

        assertSame(public, result.result)
        assertEquals(0, authenticatedCalls)
        assertEquals(false, result.authenticated)
    }

    @Test
    fun `fills missing authenticated uploader metadata from public extraction`() {
        val authenticated = testStreamResponse().copy(
            id = "abc123",
            title = "YouTube video abc123",
            uploaderName = "",
            uploaderUrl = "https://youtube.com/channel/tv",
            uploaderAvatarUrl = "",
            uploaderSubscriberCount = -1,
        )
        val public = testStreamResponse().copy(
            id = "abc123",
            title = "Public title",
            uploaderName = "Public channel",
            uploaderUrl = "https://youtube.com/@public",
            uploaderAvatarUrl = "https://example.test/avatar.jpg",
            uploaderSubscriberCount = 42,
            uploaderVerified = true,
        )

        val result = ExtractionResult.Success(authenticated).withPublicStreamMetadata(
            ExtractionResult.Success(public),
        ) as ExtractionResult.Success

        assertEquals("Public title", result.data.title)
        assertEquals("Public channel", result.data.uploaderName)
        assertEquals("https://youtube.com/channel/tv", result.data.uploaderUrl)
        assertEquals("https://example.test/avatar.jpg", result.data.uploaderAvatarUrl)
        assertEquals(42, result.data.uploaderSubscriberCount)
        assertEquals(true, result.data.uploaderVerified)
    }

    @Test
    fun `preserves specific authenticated title over public title`() {
        val authenticated = ExtractionResult.Success(testStreamResponse().copy(title = "Authenticated title"))
        val public = ExtractionResult.Success(testStreamResponse().copy(title = "Public title"))

        val result = authenticated.withPublicStreamMetadata(public) as ExtractionResult.Success

        assertEquals("Authenticated title", result.data.title)
    }

    @Test
    fun `fills authenticated metadata and recommendations from public extraction`() {
        val related = listOf(testVideoItem().copy(id = "related"))
        val authenticated = ExtractionResult.Success(
            testStreamResponse().copy(
                viewCount = -1,
                likeCount = -1,
                dislikeCount = -1,
                uploaded = -1,
                relatedStreams = emptyList(),
                hlsUrl = "/streams/hls-manifest?token=authenticated",
            ),
        )
        val public = ExtractionResult.Success(
            testStreamResponse().copy(
                viewCount = 1_234,
                likeCount = 56,
                dislikeCount = 7,
                uploaded = 1_700_000_000_000L,
                relatedStreams = related,
                hlsUrl = "/public.m3u8",
            ),
        )

        val result = authenticated.withPublicStreamMetadata(public) as ExtractionResult.Success

        assertEquals(1_234, result.data.viewCount)
        assertEquals(56, result.data.likeCount)
        assertEquals(7, result.data.dislikeCount)
        assertEquals(1_700_000_000_000L, result.data.uploaded)
        assertEquals(related, result.data.relatedStreams)
        assertEquals("/streams/hls-manifest?token=authenticated", result.data.hlsUrl)
    }

    @Test
    fun `leaves authenticated response unchanged when public extraction has no metadata`() {
        val authenticated = ExtractionResult.Success(testStreamResponse().copy(uploaderName = "TV channel"))
        val fallback = ExtractionResult.Failure("not available")

        assertSame(authenticated, authenticated.withPublicStreamMetadata(fallback))
    }

    @Test
    fun `fills missing related videos from the dedicated fallback`() = kotlinx.coroutines.runBlocking {
        val related = listOf(testVideoItem().copy(id = "related"))
        val resolution = StreamResolution(ExtractionResult.Success(testStreamResponse()))

        val result = resolution.withRelatedVideos("https://www.youtube.com/watch?v=test") {
            ExtractionResult.Success(related)
        }

        val success = result.result as ExtractionResult.Success
        assertEquals(related, success.data.relatedStreams)
    }

    @Test
    fun `keeps existing related videos without calling the fallback`() = kotlinx.coroutines.runBlocking {
        val existing = listOf(testVideoItem().copy(id = "existing"))
        val resolution = StreamResolution(
            ExtractionResult.Success(testStreamResponse().copy(relatedStreams = existing)),
        )
        var calls = 0

        val result = resolution.withRelatedVideos("https://www.youtube.com/watch?v=test") {
            calls += 1
            ExtractionResult.Success(emptyList())
        }

        assertEquals(existing, (result.result as ExtractionResult.Success).data.relatedStreams)
        assertEquals(0, calls)
    }
}
