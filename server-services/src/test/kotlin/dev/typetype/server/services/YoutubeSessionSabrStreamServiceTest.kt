package dev.typetype.server.services

import dev.typetype.server.FakeCacheService
import dev.typetype.server.testStreamResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class YoutubeSessionSabrStreamServiceTest {
    @Test
    fun cachesAuthenticatedResponsesPerUserAndVideo() = runTest {
        val metadataService = mockk<YoutubeSessionStreamService>(relaxed = true)
        val infoService = mockk<AuthenticatedSabrInfoService>()
        val cache = FakeCacheService()
        coEvery { metadataService.getStreamInfo(any(), any()) } returns null
        coEvery { infoService.oauthStream(any(), any()) } answers {
            ExtractionResult.Success(
                testStreamResponse().copy(title = "${firstArg<String>()}-${secondArg<String>()}"),
            )
        }
        val service = YoutubeSessionSabrStreamService(metadataService, infoService, cache)

        val firstA = service.getStreamInfo("user-a", YOUTUBE_URL) as ExtractionResult.Success
        val secondA = service.getStreamInfo("user-a", YOUTUBE_URL) as ExtractionResult.Success
        val firstB = service.getStreamInfo("user-b", YOUTUBE_URL) as ExtractionResult.Success
        val anotherVideo = service.getStreamInfo(
            "user-a",
            "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
        ) as ExtractionResult.Success

        assertEquals("user-a-$VIDEO_ID", firstA.data.title)
        assertEquals(firstA, secondA)
        assertEquals("user-b-$VIDEO_ID", firstB.data.title)
        assertEquals("user-a-aqz-KE-bpKQ", anotherVideo.data.title)
        coVerify(exactly = 1) { infoService.oauthStream("user-a", VIDEO_ID) }
        coVerify(exactly = 1) { infoService.oauthStream("user-b", VIDEO_ID) }
        coVerify(exactly = 1) { infoService.oauthStream("user-a", "aqz-KE-bpKQ") }
    }

    @Test
    fun deduplicatesSimultaneousAuthenticatedLoads() = runTest {
        val metadataService = mockk<YoutubeSessionStreamService>(relaxed = true)
        val infoService = mockk<AuthenticatedSabrInfoService>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<ExtractionResult<StreamResponse>?>()
        coEvery { metadataService.getStreamInfo(USER_ID, YOUTUBE_URL) } returns null
        coEvery { infoService.oauthStream(USER_ID, VIDEO_ID) } coAnswers {
            started.complete(Unit)
            release.await()
        }
        val service = YoutubeSessionSabrStreamService(metadataService, infoService, FakeCacheService())
        val first = async { service.getStreamInfo(USER_ID, YOUTUBE_URL) }
        started.await()
        val second = async { service.getStreamInfo(USER_ID, YOUTUBE_URL) }
        yield()
        release.complete(ExtractionResult.Success(testStreamResponse()))

        assertEquals(first.await(), second.await())
        coVerify(exactly = 1) { infoService.oauthStream(USER_ID, VIDEO_ID) }
    }

    @Test
    fun `a waiter still gets the stream when the caller that started the load leaves`() = runTest {
        val metadataService = mockk<YoutubeSessionStreamService>(relaxed = true)
        val infoService = mockk<AuthenticatedSabrInfoService>()
        val started = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val expected = ExtractionResult.Success(testStreamResponse())
        coEvery { metadataService.getStreamInfo(USER_ID, YOUTUBE_URL) } returns null
        coEvery { infoService.oauthStream(USER_ID, VIDEO_ID) } coAnswers {
            if (calls.incrementAndGet() == 1) {
                started.complete(Unit)
                awaitCancellation()
            }
            expected
        }
        val service = YoutubeSessionSabrStreamService(metadataService, infoService, FakeCacheService())

        val leader = launch { service.getStreamInfo(USER_ID, YOUTUBE_URL) }
        started.await()
        val waiter = async { service.getStreamInfo(USER_ID, YOUTUBE_URL) }
        yield()
        leader.cancel()

        assertEquals(expected, waiter.await())
        assertEquals(2, calls.get())
    }

    @Test
    fun `returns null for live metadata so the public HLS path is used`() = runTest {
        val metadataService = mockk<YoutubeSessionStreamService>()
        val infoService = mockk<AuthenticatedSabrInfoService>()
        val live = mockk<StreamResponse>()
        every { live.isLive } returns true
        val expected = ExtractionResult.Success(live)
        coEvery { metadataService.getStreamInfo(USER_ID, YOUTUBE_URL) } returns expected
        val service = YoutubeSessionSabrStreamService(metadataService, infoService, FakeCacheService())

        assertEquals(null, service.getStreamInfo(USER_ID, YOUTUBE_URL))
        coVerify(exactly = 0) { infoService.fetch(USER_ID, VIDEO_ID) }
    }

    private companion object {
        const val USER_ID = "user-id"
        const val VIDEO_ID = "GlzleRbo5E0"
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=$VIDEO_ID"
    }
}
