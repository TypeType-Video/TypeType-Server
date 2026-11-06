package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.routes.startupCacheOrUpstream
import dev.typetype.server.services.ProviderMediaStartupCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

class ProviderMediaStartupRaceTest {
    private val url = "https://upos-sz-mirrorcosov.bilivideo.com/a.m4s"
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun upstreamAnswer(closed: AtomicBoolean = AtomicBoolean()) = ExtractionResult.Success(
        ProxyResponse(206, "video/mp4", 8, "bytes 0-7/100", "bytes", ByteArrayInputStream("upstream".toByteArray()), close = { closed.set(true) }),
    )

    private fun body(result: ExtractionResult<ProxyResponse>) =
        (result as ExtractionResult.Success).data.stream.readBytes().decodeToString()

    @Test
    fun `asks upstream when the warmup stays stuck`() = runBlocking {
        val cache = ProviderMediaStartupCache()
        cache.begin(url)

        val started = System.nanoTime()
        val result = startupCacheOrUpstream(scope, cache, url, "bytes=0-7", hedgeAfterMillis = 50) { upstreamAnswer() }

        assertEquals("upstream", body(result))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000)
    }

    @Test
    fun `serves the warmup when it answers first and closes the upstream answer`() = runBlocking {
        val cache = ProviderMediaStartupCache()
        val claim = requireNotNull(cache.begin(url))
        val closed = AtomicBoolean()
        scope.launch {
            delay(100)
            cache.put(url, ProviderMediaStartupCache.Entry("cachedxx".toByteArray(), 100, "video/mp4", complete = false))
            cache.finish(url, claim)
        }

        val result = startupCacheOrUpstream(scope, cache, url, "bytes=0-7", hedgeAfterMillis = 50) {
            withContext(NonCancellable) { delay(500) }
            upstreamAnswer(closed)
        }

        assertEquals("cachedxx", body(result))
        withTimeout(3_000) { while (!closed.get()) delay(20) }
    }

    @Test
    fun `asks upstream at once when nothing is warming`() = runBlocking {
        val result = startupCacheOrUpstream(scope, ProviderMediaStartupCache(), url, "bytes=0-7") { upstreamAnswer() }

        assertEquals("upstream", body(result))
    }
}
