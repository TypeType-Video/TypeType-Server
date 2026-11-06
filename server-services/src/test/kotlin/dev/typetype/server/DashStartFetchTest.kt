package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.services.DashStartFetch
import dev.typetype.server.services.ProviderMediaAwareProxyService
import dev.typetype.server.services.ProviderMediaStartupCache
import dev.typetype.server.services.ProviderMediaTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DashStartFetchTest {
    private class HeldStream(private val bytes: ByteArray, private val holdAt: Int, private val release: CountDownLatch) :
        InputStream() {
        private var position = 0

        override fun read(): Int = throw UnsupportedOperationException()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position == holdAt) release.await(5, TimeUnit.SECONDS)
            if (position >= bytes.size) return -1
            val end = if (position < holdAt) minOf(holdAt, position + length) else minOf(bytes.size, position + length)
            bytes.copyInto(buffer, offset, position, end)
            return (end - position).also { position = end }
        }
    }

    private class OneResponse(private val body: InputStream, private val total: Int) : ProviderMediaAwareProxyService {
        val ranges = mutableListOf<String?>()

        override suspend fun pipeProviderMedia(url: String, rangeHeader: String?, domandBid: String?) =
            pipeProviderMediaWithAlternates(url, rangeHeader, domandBid, emptyList())

        override suspend fun pipeProviderMediaWithAlternates(
            url: String,
            rangeHeader: String?,
            domandBid: String?,
            alternateUrls: List<String>,
        ): ExtractionResult<ProxyResponse> {
            ranges += rangeHeader
            val last = minOf(requireNotNull(rangeHeader).substringAfter('-').toInt(), total - 1)
            return ExtractionResult.Success(
                ProxyResponse(206, "video/mp4", last + 1L, "bytes 0-$last/$total", "bytes", body, close = {}),
            )
        }
    }

    @Test
    fun `serves the header while the first segment still downloads`() = runBlocking {
        val sidx = sidx(firstSegmentSize = 40_000)
        val indexEnd = 1_000L + sidx.size - 1
        val file = ByteArray(1_000) + sidx + ByteArray(60_000) { 7 }
        val release = CountDownLatch(1)
        val proxy = OneResponse(HeldStream(file, (indexEnd + 1).toInt(), release), file.size)
        val cache = ProviderMediaStartupCache()
        val target = ProviderMediaTarget(url = "https://upos-sz-mirrorcosov.bilivideo.com/start.m4s")
        val headerSeen = CompletableDeferred<DashStartFetch.Header>()

        val done = async(Dispatchers.IO) {
            DashStartFetch(proxy, cache).fetch(target, 1_000, indexEnd, 800_000) { headerSeen.complete(it) }
        }
        val header = withTimeout(5_000) { headerSeen.await() }

        assertEquals(indexEnd + 40_000, header.segments?.first()?.last)
        assertNotNull(cache.respond(target.url, "bytes=1000-$indexEnd"))
        assertNull(cache.respond(target.url, "bytes=${indexEnd + 1}-${indexEnd + 40_000}"))
        release.countDown()
        assertTrue(done.await())
        assertNotNull(cache.respond(target.url, "bytes=${indexEnd + 1}-${indexEnd + 40_000}"))
        assertEquals(listOf<String?>("bytes=0-${indexEnd + 1_000_000}"), proxy.ranges)
    }

    private fun sidx(firstSegmentSize: Int): ByteArray {
        val size = 8 + 4 + 8 + 8 + 4 + 12
        val buffer = ByteBuffer.allocate(size)
        buffer.putInt(size).put("sidx".toByteArray()).putInt(0).putInt(1).putInt(1000)
        buffer.putInt(0).putInt(0).putShort(0).putShort(1)
        buffer.putInt(firstSegmentSize).putInt(5000).putInt(0)
        return buffer.array()
    }
}
