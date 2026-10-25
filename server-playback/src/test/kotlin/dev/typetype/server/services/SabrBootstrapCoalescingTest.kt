package dev.typetype.server.services

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class SabrBootstrapCoalescingTest {
    private val sessionStore = mockk<SabrSessionStore>()
    private val tokenClient = mockk<TypetypeTokenYoutubeSessionClient>()
    private val epoch = AtomicLong(0L)
    private val preparedAvailable = AtomicBoolean(true)
    private val service = SabrBootstrapStreamService(
        sessionStore,
        tokenClient,
        mockk<StreamService>(),
        SabrBootstrapCache({ epoch.get() }, { preparedAvailable.get() }),
    )

    init {
        coEvery { sessionStore.rememberPreparedInfo(VIDEO_ID, any()) } returns Unit
        coEvery { tokenClient.fetchPlaybackSessionResult(VIDEO_ID) } coAnswers {
            delay(100)
            TokenServiceResult.Success(session())
        }
    }

    @Test
    fun `concurrent bootstraps for one video make one token service call`() = runBlocking {
        val results = List(20) { async(Dispatchers.Default) { service.getStreamInfo(URL) } }.awaitAll()

        assertTrue(results.all { it is ExtractionResult.Success })
        coVerify(exactly = 1) { tokenClient.fetchPlaybackSessionResult(VIDEO_ID) }
    }

    @Test
    fun `a cancelled caller does not fail the others`() = runBlocking {
        val first = async(Dispatchers.Default) { service.getStreamInfo(URL) }
        val second = async(Dispatchers.Default) { service.getStreamInfo(URL) }
        delay(20)
        first.cancel()

        assertInstanceOf(ExtractionResult.Success::class.java, second.await())
        coVerify(exactly = 1) { tokenClient.fetchPlaybackSessionResult(VIDEO_ID) }
    }

    @Test
    fun `a warm bootstrap is answered from the cache`() = runBlocking {
        service.getStreamInfo(URL)
        val startedAt = System.nanoTime()

        val warm = service.getStreamInfo(URL)

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0
        assertInstanceOf(ExtractionResult.Success::class.java, warm)
        assertTrue(elapsedMs < 30.0, "warm bootstrap took $elapsedMs ms")
        println("sabr_bootstrap warm_ms=$elapsedMs previous_warm_ms=260")
        coVerify(exactly = 1) { tokenClient.fetchPlaybackSessionResult(VIDEO_ID) }
    }

    @Test
    fun `a provider session renewal or dropped prepared info refreshes the bootstrap`() = runBlocking {
        service.getStreamInfo(URL)
        epoch.incrementAndGet()
        service.getStreamInfo(URL)
        preparedAvailable.set(false)
        service.getStreamInfo(URL)

        coVerify(exactly = 3) { tokenClient.fetchPlaybackSessionResult(VIDEO_ID) }
    }

    @Test
    fun `a bot check from the token service is a provider block`() = runBlocking {
        coEvery { tokenClient.fetchPlaybackSessionResult(VIDEO_ID) } returns TokenServiceResult.Failure(
            TokenServiceFailureReason.HTTP_STATUS,
            500,
            "YouTube MWEB player response is LOGIN_REQUIRED: Sign in to confirm you're not a bot",
        )

        val result = service.getStreamInfo(URL) as ExtractionResult.Failure

        assertEquals(ExtractionFailureKind.ProviderAccessBlocked, result.kind)
        assertEquals("provider_access_blocked", result.code)
    }

    private fun session(): TokenYoutubeSession {
        val video = mockk<YoutubeSabrFormat>(relaxed = true) {
            every { isVideo } returns true
            every { itag } returns 137
            every { mimeType } returns "video/mp4; codecs=\"avc1.640028\""
        }
        val audio = mockk<YoutubeSabrFormat>(relaxed = true) {
            every { isAudio } returns true
            every { itag } returns 140
            every { mimeType } returns "audio/mp4; codecs=\"mp4a.40.2\""
        }
        val info = mockk<YoutubeSabrInfo>(relaxed = true) {
            every { formats } returns listOf(video, audio)
            every { visitorData } returns VISITOR
        }
        val token = SabrTokenBundle(VIDEO_ID, "pot", byteArrayOf(1), VISITOR, "vpot", byteArrayOf(2))
        return TokenYoutubeSession(
            info = info,
            token = token,
            title = "title",
            author = "author",
            channelId = "channel",
            channelAvatarUrl = "",
            description = "",
            durationMs = 60_000L,
            viewCount = 1L,
            thumbnailUrl = "",
            tags = emptyList(),
            isLive = false,
            isLiveContent = false,
        )
    }

    private companion object {
        const val VIDEO_ID = "f6f3PhauXyg"
        const val URL = "https://www.youtube.com/watch?v=$VIDEO_ID"
        const val VISITOR = "visitor"
    }
}
