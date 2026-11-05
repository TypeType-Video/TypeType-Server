package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class ProviderMediaWarmup(
    private val handleService: ProviderMediaHandleService,
    private val proxyService: ProviderMediaAwareProxyService,
    private val cache: ProviderMediaStartupCache,
    private val scope: CoroutineScope,
) {
    private val permits = Semaphore(MAX_PARALLEL_FETCHES)

    fun warm(response: StreamResponse, provider: ProviderMediaType): Job =
        scope.launch {
            withTimeoutOrNull(WARMUP_TIMEOUT_MILLIS) {
                when (provider) {
                    ProviderMediaType.BILIBILI -> warmDash(response)
                    ProviderMediaType.NICONICO -> handleOf(response.hlsUrl)?.let { warmHls(it) }
                }
            }
        }

    suspend fun prefetch(handle: String, rangeHeader: String?, limit: Int): ByteArray? {
        cache.get(handle)?.let { return it.bytes }
        val claim = cache.begin(handle) ?: run {
            cache.awaitPending(handle)
            return cache.get(handle)?.bytes
        }
        return try {
            permits.withPermit { fetch(handle, rangeHeader, limit) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } finally {
            cache.finish(handle, claim)
        }
    }

    private suspend fun fetch(handle: String, rangeHeader: String?, limit: Int): ByteArray? {
        val target = handleService.resolve(handle) ?: return null
        val result = proxyService.pipeProviderMediaWithAlternates(
            target.url,
            rangeHeader,
            target.domandBid,
            target.alternateUrls,
        )
        if (result !is ExtractionResult.Success) return null
        val proxy = result.data
        try {
            if (proxy.status != 200 && proxy.status != 206) return null
            val bytes = runInterruptible(Dispatchers.IO) { readAtMost(proxy.stream, limit) } ?: return null
            val total = proxy.contentRange?.substringAfterLast('/')?.toLongOrNull()
                ?: proxy.contentLength.takeIf { rangeHeader == null }
            val complete = rangeHeader == null || (total != null && bytes.size.toLong() == total)
            cache.put(handle, ProviderMediaStartupCache.Entry(bytes, total, proxy.contentType, complete))
            return bytes
        } finally {
            proxy.close()
        }
    }

    private suspend fun warmDash(response: StreamResponse) = coroutineScope {
        val ranges = response.videoOnlyStreams.map { it.url to it.indexEnd } +
            response.videoStreams.map { it.url to it.indexEnd } +
            response.audioStreams.map { it.url to it.indexEnd }
        ranges.mapNotNull { (url, indexEnd) ->
            val handle = handleOf(url) ?: return@mapNotNull null
            if (indexEnd <= 0 || indexEnd >= MAX_DASH_HEADER_BYTES) return@mapNotNull null
            async { prefetch(handle, "bytes=0-$indexEnd", MAX_DASH_HEADER_BYTES) }
        }.awaitAll()
    }

    private suspend fun warmHls(masterHandle: String) {
        val master = prefetch(masterHandle, null, MAX_PLAYLIST_BYTES)?.toString(StandardCharsets.UTF_8) ?: return
        coroutineScope {
            HlsWarmupPlan.fromMaster(master).map { child ->
                async {
                    val playlist = prefetch(child.handle, null, MAX_PLAYLIST_BYTES)
                        ?.toString(StandardCharsets.UTF_8) ?: return@async
                    val media = HlsWarmupPlan.fromMediaPlaylist(playlist)
                    media.headers.forEach { prefetch(it, null, MAX_SEGMENT_BYTES) }
                    if (child.startsPlayback) media.firstSegment?.let { prefetch(it, null, MAX_SEGMENT_BYTES) }
                }
            }.awaitAll()
        }
    }

    private fun handleOf(url: String): String? =
        url.substringAfterLast("/media/", "").takeIf(::isProviderMediaHandle)

    private fun readAtMost(stream: java.io.InputStream, limit: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) return output.toByteArray()
            if (output.size() + read > limit) return null
            output.write(buffer, 0, read)
        }
    }

    companion object {
        const val MAX_PARALLEL_FETCHES = 12
        const val MAX_DASH_HEADER_BYTES = 64 * 1024
        const val MAX_PLAYLIST_BYTES = 512 * 1024
        const val MAX_SEGMENT_BYTES = 4 * 1024 * 1024
        const val WARMUP_TIMEOUT_MILLIS = 20_000L
    }
}
