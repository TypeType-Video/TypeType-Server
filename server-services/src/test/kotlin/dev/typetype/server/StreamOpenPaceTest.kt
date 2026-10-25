package dev.typetype.server

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.CachedStreamService
import dev.typetype.server.services.StreamOpenPace
import dev.typetype.server.services.StreamRequestWatch
import dev.typetype.server.services.StreamService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamOpenPaceTest {
    private class Provider : StreamService {
        val askedAt = mutableListOf<Long>()
        var clock: () -> Long = { 0L }

        override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
            askedAt += clock()
            return ExtractionResult.Failure("no such video")
        }
    }

    @Test
    fun `without a limit every video is opened at once`() = runTest {
        val pace = StreamOpenPace(perMinute = 0, now = { currentTime })
        repeat(50) { assertEquals(true, pace.turn()) }
        assertEquals(0L, currentTime)
    }

    @Test
    fun `videos asked together are opened evenly spaced and one alone does not wait`() = runTest {
        val provider = Provider().also { it.clock = { currentTime } }
        val service = CachedStreamService(provider, FakeCacheService(), pace = StreamOpenPace(perMinute = 120, now = { currentTime }))
        (1..5).map { video -> async { service.getStreamInfo("https://youtu.be/v$video") } }.awaitAll()
        assertEquals(listOf(0L, 500L, 1_000L, 1_500L, 2_000L), provider.askedAt.sorted())
        testScheduler.advanceTimeBy(10_000)
        val before = currentTime
        service.getStreamInfo("https://youtu.be/alone")
        assertEquals(before, provider.askedAt.last())
    }

    @Test
    fun `a video whose turn is too far away is not asked and one nobody waits for never queues`() = runTest {
        val provider = Provider().also { it.clock = { currentTime } }
        val pace = StreamOpenPace(perMinute = 60, longestWaitMs = 2_000, now = { currentTime })
        val service = CachedStreamService(provider, FakeCacheService(), watch = StreamRequestWatch(), pace = pace)
        val answers = (1..6).map { video -> async { service.getStreamInfo("https://youtu.be/w$video") as ExtractionResult.Failure } }.awaitAll()
        assertEquals(3, provider.askedAt.size)
        assertEquals(3, answers.count { it.code == CachedStreamService.PACED_CODE })
        answers.filter { it.code == CachedStreamService.PACED_CODE }.forEach { paced ->
            assertEquals(ExtractionFailureKind.RateLimited, paced.kind)
            assertEquals(CachedStreamService.PACED_RETRY_AFTER_SECONDS, paced.retryAfterSeconds)
        }
        val background = withContext(StreamRequestWatch.NobodyWaiting.mark) { service.getStreamInfo("https://youtu.be/hover") } as ExtractionResult.Failure
        assertEquals(CachedStreamService.PACED_CODE, background.code)
        assertEquals(3, provider.askedAt.size)
    }

    @Test
    fun `another provider's videos are not paced`() = runTest {
        val provider = Provider().also { it.clock = { currentTime } }
        val service = CachedStreamService(provider, FakeCacheService(), pace = StreamOpenPace(perMinute = 1, now = { currentTime }))
        repeat(4) { service.getStreamInfo("https://www.bilibili.com/video/BV$it") }
        assertEquals(listOf(0L, 0L, 0L, 0L), provider.askedAt)
    }
}
