package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.VideoStreamItem
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
import java.io.InputStream
import java.nio.charset.StandardCharsets

class ProviderMediaWarmup(
    private val handleService: ProviderMediaHandleService,
    private val proxyService: ProviderMediaAwareProxyService,
    private val cache: ProviderMediaStartupCache,
    private val scope: CoroutineScope,
) {
    private class Media(val url: String, val indexEnd: Long, val startsPlayback: Boolean)

    private val permits = Semaphore(MAX_PARALLEL_FETCHES)

    fun warm(response: StreamResponse, provider: ProviderMediaType): Job =
        warm(provider, response.videoOnlyStreams + response.videoStreams, response.audioStreams)

    fun warm(provider: ProviderMediaType, video: List<VideoStreamItem>, audio: List<AudioStreamItem>): Job =
        scope.launch {
            withTimeoutOrNull(WARMUP_TIMEOUT_MILLIS) {
                val lowest = video.minByOrNull { it.height.takeIf { height -> height > 0 } ?: it.bitrate ?: Int.MAX_VALUE }?.url
                val media = video.map { Media(it.url, it.indexEnd, it.url == lowest) } +
                    audio.map { Media(it.url, it.indexEnd, startsPlayback = true) }
                coroutineScope {
                    media.map { item ->
                        async {
                            val target = targetOf(item.url) ?: return@async
                            when (provider) {
                                ProviderMediaType.BILIBILI -> warmDash(target, item.indexEnd)
                                ProviderMediaType.NICONICO -> warmHlsMedia(target, item.startsPlayback)
                            }
                        }
                    }.awaitAll()
                }
            }
        }

    suspend fun prefetch(target: ProviderMediaTarget, rangeHeader: String?, limit: Int): ByteArray? {
        cache.get(target.url)?.let { return it.bytes }
        val claim = cache.begin(target.url) ?: run {
            cache.awaitPending(target.url)
            return cache.get(target.url)?.bytes
        }
        return try {
            permits.withPermit { fetch(target, rangeHeader, limit) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } finally {
            cache.finish(target.url, claim)
        }
    }

    private suspend fun warmDash(target: ProviderMediaTarget, indexEnd: Long) {
        if (indexEnd <= 0 || indexEnd >= MAX_DASH_HEADER_BYTES) return
        prefetch(target, "bytes=0-$indexEnd", MAX_DASH_HEADER_BYTES)
    }

    private suspend fun warmHlsMedia(target: ProviderMediaTarget, startsPlayback: Boolean) {
        val playlist = prefetch(target, null, MAX_PLAYLIST_BYTES)?.toString(StandardCharsets.UTF_8) ?: return
        val media = HlsWarmupPlan.fromMediaPlaylist(playlist)
        media.headers.forEach { header -> targetOf(header)?.let { prefetch(it, null, MAX_SEGMENT_BYTES) } }
        if (startsPlayback) {
            media.firstSegment?.let { targetOf(it) }?.let { prefetch(it, null, MAX_SEGMENT_BYTES) }
        }
    }

    private suspend fun targetOf(reference: String): ProviderMediaTarget? {
        val handle = reference.substringBefore('?').substringAfterLast('/')
        return if (isProviderMediaHandle(handle)) {
            runCatching { handleService.resolve(handle) }.getOrNull()
        } else {
            handleService.upstreamTarget(reference)
        }
    }

    private suspend fun fetch(target: ProviderMediaTarget, rangeHeader: String?, limit: Int): ByteArray? {
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
            cache.put(target.url, ProviderMediaStartupCache.Entry(bytes, total, proxy.contentType, complete))
            return bytes
        } finally {
            proxy.close()
        }
    }

    private fun readAtMost(stream: InputStream, limit: Int): ByteArray? {
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
