package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.CachedRelatedVideosService
import dev.typetype.server.services.RelatedVideosService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class CachedRelatedVideosServiceTest {
    @Test
    fun `cached results are shared across URL forms for the same video`() = runBlocking {
        var calls = 0
        val related = listOf(testVideoItem().copy(id = "related"))
        val service = CachedRelatedVideosService(
            RelatedVideosService {
                calls += 1
                ExtractionResult.Success(related)
            },
            FakeCacheService(),
        )

        assertEquals(ExtractionResult.Success(related), service.related(YOUTUBE_URL))
        assertEquals(ExtractionResult.Success(related), service.related(SHORT_URL))
        assertEquals(1, calls)
    }

    @Test
    fun `successful empty results are kept for thirty seconds only`() = runBlocking {
        var calls = 0
        val cache = ExpiringFakeCache()
        val service = CachedRelatedVideosService(
            RelatedVideosService {
                calls += 1
                ExtractionResult.Success(emptyList())
            },
            cache,
        )

        assertEquals(ExtractionResult.Success(emptyList<VideoItem>()), service.related(YOUTUBE_URL))
        assertEquals(ExtractionResult.Success(emptyList<VideoItem>()), service.related(YOUTUBE_URL))
        assertEquals(1, calls)
        assertEquals(setOf("youtube-related:v1:$VIDEO_ID"), cache.keys())
        assertEquals(30L, cache.ttls["youtube-related:v1:$VIDEO_ID"])
    }

    @Test
    fun `concurrent cache misses share one delegate request`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val related = listOf(testVideoItem().copy(id = "related"))
        val service = CachedRelatedVideosService(
            RelatedVideosService {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
                ExtractionResult.Success(related)
            },
            FakeCacheService(),
        )

        val first = async(start = CoroutineStart.UNDISPATCHED) { service.related(YOUTUBE_URL) }
        started.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { service.related(YOUTUBE_URL) }
        release.complete(Unit)

        assertEquals(ExtractionResult.Success(related), first.await())
        assertEquals(ExtractionResult.Success(related), second.await())
        assertEquals(1, calls.get())
    }

    @Test
    fun `failures back off before the provider is asked again`() = runBlocking {
        var calls = 0
        val failure = ExtractionResult.Failure("temporary upstream error")
        val service = CachedRelatedVideosService(
            RelatedVideosService {
                calls += 1
                failure
            },
            FakeCacheService(),
        )

        assertEquals(failure, service.related(YOUTUBE_URL))
        assertEquals(failure, service.related(YOUTUBE_URL))
        assertEquals(1, calls)
    }

    @Test
    fun `an empty result after a good one serves the good videos`() = runBlocking {
        val cache = ExpiringFakeCache()
        val related = listOf(testVideoItem().copy(id = "related"))
        var next: List<VideoItem> = related
        val service = CachedRelatedVideosService(RelatedVideosService { ExtractionResult.Success(next) }, cache)
        service.related(YOUTUBE_URL)
        cache.delete("youtube-related:v1:$VIDEO_ID")
        next = emptyList()

        assertEquals(ExtractionResult.Success(related), service.related(YOUTUBE_URL))
    }

    private companion object {
        const val VIDEO_ID = "dQw4w9WgXcQ"
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=$VIDEO_ID"
        const val SHORT_URL = "https://youtu.be/$VIDEO_ID?t=30"
    }
}
