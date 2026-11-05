package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.services.ProviderMediaAwareProxyService
import dev.typetype.server.services.ProviderMediaHandleService
import dev.typetype.server.services.ProviderMediaReadAhead
import dev.typetype.server.services.ProviderMediaSegmentTable
import dev.typetype.server.services.ProviderMediaStartupCache
import dev.typetype.server.services.ProviderMediaWarmup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.Collections

class ProviderMediaReadAheadTest {
    private class RangeProxy(private val file: ByteArray) : ProviderMediaAwareProxyService {
        val ranges: MutableList<String?> = Collections.synchronizedList(mutableListOf())

        override suspend fun pipeProviderMedia(url: String, rangeHeader: String?, domandBid: String?) =
            respond(rangeHeader)

        override suspend fun pipeProviderMediaWithAlternates(
            url: String,
            rangeHeader: String?,
            domandBid: String?,
            alternateUrls: List<String>,
        ) = respond(rangeHeader)

        private fun respond(rangeHeader: String?): ExtractionResult<ProxyResponse> {
            ranges += rangeHeader
            val (first, last) = requireNotNull(rangeHeader).removePrefix("bytes=").split('-').map(String::toInt)
            val body = file.copyOfRange(first, last + 1)
            return ExtractionResult.Success(
                ProxyResponse(
                    status = 206,
                    contentType = "video/mp4",
                    contentLength = body.size.toLong(),
                    contentRange = "bytes $first-$last/${file.size}",
                    acceptRanges = "bytes",
                    stream = ByteArrayInputStream(body),
                    close = {},
                ),
            )
        }
    }

    @Test
    fun `fetches the next segment while the current one is served`() = runBlocking {
        val handles = ProviderMediaHandleService(FakeCacheService())
        val path = handles.createPath("https://upos-sz-mirrorcosov.bilivideo.com/v.m4s?deadline=4000000000")
        val target = requireNotNull(handles.resolve(path.substringAfterLast('/')))
        val file = ByteArray(3_000) { (it % 251).toByte() }
        val proxy = RangeProxy(file)
        val cache = ProviderMediaStartupCache()
        val segments = ProviderMediaSegmentTable()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val warmup = ProviderMediaWarmup(handles, proxy, cache, scope, segments)
        segments.remember(target.url, listOf(1_000L..1_999L, 2_000L..2_999L))

        ProviderMediaReadAhead(warmup, segments).onServed("m1_x", target, "bytes=1000-1999")
        withTimeout(5_000) {
            while (cache.respond(target.url, "bytes=2000-2999") == null) delay(10)
        }

        assertEquals(listOf<String?>("bytes=2000-2999"), proxy.ranges.toList())
        val served = requireNotNull(cache.respond(target.url, "bytes=2000-2999"))
        assertEquals("bytes 2000-2999/3000", served.contentRange)
        assertEquals(file.copyOfRange(2_000, 3_000).toList(), served.stream.readBytes().toList())
        assertNull(cache.respond(target.url, "bytes=1000-1999"))
    }

    @Test
    fun `does nothing for a range that does not start a known segment`() = runBlocking {
        val handles = ProviderMediaHandleService(FakeCacheService())
        val path = handles.createPath("https://upos-sz-mirrorcosov.bilivideo.com/v.m4s?deadline=4000000000")
        val target = requireNotNull(handles.resolve(path.substringAfterLast('/')))
        val proxy = RangeProxy(ByteArray(3_000))
        val segments = ProviderMediaSegmentTable()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val warmup = ProviderMediaWarmup(handles, proxy, ProviderMediaStartupCache(), scope, segments)
        segments.remember(target.url, listOf(1_000L..1_999L, 2_000L..2_999L))

        val readAhead = ProviderMediaReadAhead(warmup, segments)
        readAhead.onServed("m1_x", target, "bytes=1500-1999")
        readAhead.onServed("m1_x", target, "bytes=2000-2999")
        readAhead.onServed("m1_x", target, null)

        assertEquals(emptyList<String?>(), proxy.ranges.toList())
    }
}
