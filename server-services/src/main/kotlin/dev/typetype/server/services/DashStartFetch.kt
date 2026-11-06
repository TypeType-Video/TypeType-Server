package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.InputStream

/**
 * The start track needs its header and its first segment before the first frame. One upstream
 * request brings both: the header reaches the cache as soon as it arrives, so the player reads the
 * index meanwhile, and the segment follows on the same response instead of a second CDN trip.
 */
internal class DashStartFetch(
    private val proxyService: ProviderMediaAwareProxyService,
    private val cache: ProviderMediaStartupCache,
) {
    class Header(val bytes: ByteArray, val totalLength: Long?, val segments: List<LongRange>?)

    /** Returns false when another fetch owns the track or the first segment ends past the guess. */
    suspend fun fetch(
        target: ProviderMediaTarget,
        indexStart: Long,
        indexEnd: Long,
        bitrate: Int?,
        onHeader: (Header) -> Unit,
    ): Boolean {
        val claim = cache.begin(target.url) ?: return false
        return try {
            fetchClaimed(target, indexStart, indexEnd, indexEnd + guessBytes(bitrate), onHeader)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            true
        } finally {
            cache.finish(target.url, claim)
        }
    }

    private suspend fun fetchClaimed(
        target: ProviderMediaTarget,
        indexStart: Long,
        indexEnd: Long,
        last: Long,
        onHeader: (Header) -> Unit,
    ): Boolean {
        val result = proxyService.pipeProviderMediaWithAlternates(
            target.url,
            "bytes=0-$last",
            target.domandBid,
            target.alternateUrls,
        )
        if (result !is ExtractionResult.Success) return true
        val proxy = result.data
        try {
            if (proxy.status != 200 && proxy.status != 206) return true
            val total = proxy.contentRange?.substringAfterLast('/')?.toLongOrNull()
                ?: proxy.contentLength.takeIf { proxy.status == 200 }
            val header = readExactly(proxy.stream, indexEnd + 1) ?: return true
            cache.put(target.url, entry(header, total, proxy.contentType))
            val segments = Mp4SegmentIndex.segments(header, indexStart)
            onHeader(Header(header, total, segments))
            val end = segments?.firstOrNull()?.last ?: return true
            if (end < header.size) return true
            if (end > last) return false
            val rest = readExactly(proxy.stream, end + 1 - header.size) ?: return true
            cache.put(target.url, entry(header + rest, total, proxy.contentType))
            return true
        } finally {
            proxy.close()
        }
    }

    private fun entry(bytes: ByteArray, total: Long?, contentType: String) =
        ProviderMediaStartupCache.Entry(bytes, total, contentType, complete = total == bytes.size.toLong())

    private suspend fun readExactly(stream: InputStream, size: Long): ByteArray? = runInterruptible(Dispatchers.IO) {
        val bytes = ByteArray(size.toInt())
        var read = 0
        while (read < bytes.size) {
            val count = stream.read(bytes, read, bytes.size - read)
            if (count < 0) return@runInterruptible null
            read += count
        }
        bytes
    }

    private fun guessBytes(bitrate: Int?): Long {
        val bytesPerSecond = (bitrate?.takeIf { it > 0 } ?: DEFAULT_BITS_PER_SECOND) / 8L
        return (bytesPerSecond * GUESS_SECONDS).coerceIn(MIN_GUESS_BYTES, MAX_GUESS_BYTES)
    }

    private companion object {
        const val DEFAULT_BITS_PER_SECOND = 1_000_000
        const val GUESS_SECONDS = 10L
        const val MIN_GUESS_BYTES = 512L * 1024
        const val MAX_GUESS_BYTES = 3L * 1024 * 1024
    }
}
