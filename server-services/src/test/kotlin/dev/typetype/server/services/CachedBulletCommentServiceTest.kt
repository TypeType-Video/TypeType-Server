package dev.typetype.server.services

import dev.typetype.server.ExpiringFakeCache
import dev.typetype.server.models.BulletCommentItem
import dev.typetype.server.models.BulletCommentsPageResponse
import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfo
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.services.bilibili.extractors.BilibiliRiskControlException
import java.util.concurrent.atomic.AtomicInteger

class CachedBulletCommentServiceTest {

    @AfterEach
    fun tearDown() = unmockkAll()

    private fun sampleItem(text: String = "测试弹幕") = BulletCommentItem(
        text = text,
        argbColor = -1,
        position = "REGULAR",
        relativeFontSize = 0.6,
        durationMs = 1500L,
        isLive = false,
    )

    @Test
    fun `caches non-empty vod bullet comments and deduplicates concurrent misses`() = runBlocking {
        val url = "https://www.bilibili.com/video/BV1testCache01"
        val cache = ExpiringFakeCache()
        val calls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val expected = BulletCommentsPageResponse(listOf(sampleItem()), null)

        val service = CachedBulletCommentService(
            delegate = object : BulletCommentService {
                override suspend fun getBulletComments(
                    url: String,
                    nextpage: String?,
                ): ExtractionResult<BulletCommentsPageResponse> {
                    calls.incrementAndGet()
                    gate.await()
                    return ExtractionResult.Success(expected)
                }
            },
            cache = cache,
        )

        val concurrent = (1..4).map { async { service.getBulletComments(url, null) } }
        gate.complete(Unit)
        val firstBatch = concurrent.awaitAll()
        assertTrue(firstBatch.all { it == ExtractionResult.Success(expected) })
        assertEquals(1, calls.get())
        assertEquals(300L, cache.ttls[PublicCacheKey.of("bullet-comments", url, null)])

        val cached = service.getBulletComments(url, null)
        assertEquals(ExtractionResult.Success(expected), cached)
        assertEquals(1, calls.get())
    }

    @Test
    fun `empty first page receives short suspect ttl and falls back to lastGood`() = runBlocking {
        val url = "https://www.bilibili.com/video/BV1testSuspect"
        val cache = ExpiringFakeCache()
        val good = BulletCommentsPageResponse(listOf(sampleItem("历史弹幕")), null)
        val empty = BulletCommentsPageResponse(emptyList(), null)
        var next = good

        val service = CachedBulletCommentService(
            delegate = object : BulletCommentService {
                override suspend fun getBulletComments(
                    url: String,
                    nextpage: String?,
                ): ExtractionResult<BulletCommentsPageResponse> = ExtractionResult.Success(next)
            },
            cache = cache,
        )

        assertEquals(ExtractionResult.Success(good), service.getBulletComments(url, null))
        val primaryKey = PublicCacheKey.of("bullet-comments", url, null)
        cache.delete(primaryKey)
        next = empty

        val result = service.getBulletComments(url, null)
        assertEquals(ExtractionResult.Success(good), result)

        val freshUrl = "https://www.bilibili.com/video/BV1testEmptyOnly"
        assertEquals(ExtractionResult.Success(empty), service.getBulletComments(freshUrl, null))
        assertEquals(30L, cache.ttls[PublicCacheKey.of("bullet-comments", freshUrl, null)])
    }

    @Test
    fun `bilibili live url short-circuits without opening websocket or caching`() = runBlocking {
        val liveUrl = "https://live.bilibili.com/123456"
        val cache = ExpiringFakeCache()
        val service = CachedBulletCommentService(PipePipeBulletCommentService(), cache)

        val result = service.getBulletComments(liveUrl, null)

        assertEquals(ExtractionResult.Success(BulletCommentsPageResponse(emptyList(), null)), result)
        assertTrue(cache.keys().isEmpty())
    }

    @Test
    fun `pipepipe bullet comment service maps bilibili risk control and pauses its own guard`() = runBlocking {
        val url = "https://www.bilibili.com/video/BV1riskControl"
        mockkStatic(NewPipe::class)
        mockkStatic(BulletCommentsInfo::class)
        every { NewPipe.getServiceByUrl(url) } returns ServiceList.BiliBili
        every { BulletCommentsInfo.getInfo(url) } throws ExtractionException(
            "blocked",
            BilibiliRiskControlException("Bilibili risk control blocked this server (HTTP 412)", 412),
        )

        var clock = 1_000_000L
        val guard = ProviderExtractionGuard(
            provider = "bilibili-bullet-comments",
            firstPauseMs = 60_000L,
            now = { clock },
        )
        val service = PipePipeBulletCommentService(bilibiliGuard = guard)

        val first = service.getBulletComments(url, null)
        assertTrue(first is ExtractionResult.Failure)
        assertEquals(ExtractionFailureKind.RateLimited, (first as ExtractionResult.Failure).kind)
        verify(exactly = 1) { BulletCommentsInfo.getInfo(url) }

        val concurrentWhilePaused = async {
            // Second request sees the active pause without invoking the extractor when another probe holds or paced
            guard.run(nobodyWaits = true) { ExtractionResult.Success(BulletCommentsPageResponse(emptyList(), null)) }
        }.await()
        assertTrue(concurrentWhilePaused is ExtractionResult.Failure)
        assertEquals(CachedStreamService.PACED_CODE, (concurrentWhilePaused as ExtractionResult.Failure).code)
        verify(exactly = 1) { BulletCommentsInfo.getInfo(url) }
    }
}
