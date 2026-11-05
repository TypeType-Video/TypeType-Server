package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.VideoStreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
    private val segments: ProviderMediaSegmentTable = ProviderMediaSegmentTable(),
) {
    private class Media(val url: String, val indexStart: Long, val indexEnd: Long, val startsPlayback: Boolean)

    private val permits = Semaphore(MAX_PARALLEL_FETCHES)

    fun warm(response: StreamResponse, provider: ProviderMediaType): Job =
        warm(provider, response.videoOnlyStreams + response.videoStreams, response.audioStreams)

    fun warm(provider: ProviderMediaType, video: List<VideoStreamItem>, audio: List<AudioStreamItem>): Job =
        scope.launch {
            withTimeoutOrNull(WARMUP_TIMEOUT_MILLIS) {
                val starting = startingUrls(provider, video, audio)
                val media = video.map { Media(it.url, it.indexStart, it.indexEnd, it.url in starting) } +
                    audio.map { Media(it.url, it.indexStart, it.indexEnd, it.url in starting) }
                coroutineScope {
                    media.map { item ->
                        async {
                            val target = targetOf(item.url) ?: return@async
                            when (provider) {
                                ProviderMediaType.BILIBILI -> warmDash(target, item)
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
        return fetchClaimed(target, rangeHeader, limit, claim)
    }

    fun prefetchSegment(target: ProviderMediaTarget, range: LongRange): Job? {
        if (permits.availablePermits == 0) return null
        val key = ProviderMediaStartupCache.segmentKey(target.url, range.first)
        val claim = cache.begin(key) ?: return null
        return scope.launch {
            fetchClaimed(target, "bytes=${range.first}-${range.last}", MAX_SEGMENT_BYTES, claim, key, range.first)
        }.also { job -> job.invokeOnCompletion { cache.finish(key, claim) } }
    }

    private suspend fun fetchClaimed(
        target: ProviderMediaTarget,
        rangeHeader: String?,
        limit: Int,
        claim: CompletableDeferred<Unit>,
        key: String = target.url,
        offset: Long = 0,
    ): ByteArray? = try {
        permits.withPermit { fetch(target, rangeHeader, limit, key, offset) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    } finally {
        cache.finish(key, claim)
    }

    private suspend fun warmDash(target: ProviderMediaTarget, media: Media) {
        if (media.indexEnd <= 0 || media.indexEnd >= MAX_DASH_HEADER_BYTES) return
        val header = prefetch(target, "bytes=0-${media.indexEnd}", MAX_DASH_HEADER_BYTES) ?: return
        val table = Mp4SegmentIndex.segments(header, media.indexStart) ?: return
        segments.remember(target.url, table)
        if (!media.startsPlayback) return
        val end = table.first().last
        if (end < header.size || end >= MAX_SEGMENT_BYTES) return
        val claim = cache.begin(target.url, extending = true) ?: return
        fetchClaimed(target, "bytes=0-$end", MAX_SEGMENT_BYTES, claim)
    }

    private fun startingUrls(
        provider: ProviderMediaType,
        video: List<VideoStreamItem>,
        audio: List<AudioStreamItem>,
    ): Set<String> = when (provider) {
        ProviderMediaType.NICONICO -> setOfNotNull(
            video.minByOrNull { it.height.takeIf { height -> height > 0 } ?: Int.MAX_VALUE }?.url,
        ) + audio.map { it.url }
        ProviderMediaType.BILIBILI -> {
            val group = video.groupBy { webCodecRank(it.codec) }.minByOrNull { it.key }?.value.orEmpty()
            val start = group.filter { (it.bitrate ?: 0) in 1..BILIBILI_START_BITS_PER_SECOND }.maxByOrNull { it.bitrate ?: 0 }
                ?: group.minByOrNull { it.bitrate ?: Int.MAX_VALUE }
            setOfNotNull(start?.url, audio.maxByOrNull { it.bitrate ?: 0 }?.url)
        }
    }

    // The web player starts on the first codec family it supports in this order.
    private fun webCodecRank(codec: String?): Int = when {
        codec == null -> 3
        codec.startsWith("avc1") -> 0
        codec.startsWith("av01") -> 1
        codec.startsWith("hev1") || codec.startsWith("hvc1") -> 2
        else -> 3
    }

    private suspend fun warmHlsMedia(target: ProviderMediaTarget, startsPlayback: Boolean) {
        val playlist = prefetch(target, null, MAX_PLAYLIST_BYTES)?.toString(StandardCharsets.UTF_8) ?: return
        val media = HlsWarmupPlan.fromMediaPlaylist(playlist)
        val files = media.headers + listOfNotNull(media.firstSegment.takeIf { startsPlayback })
        coroutineScope {
            files.map { file ->
                async { targetOf(file)?.let { prefetch(it, null, MAX_SEGMENT_BYTES) } }
            }.awaitAll()
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

    private suspend fun fetch(
        target: ProviderMediaTarget,
        rangeHeader: String?,
        limit: Int,
        key: String = target.url,
        offset: Long = 0,
    ): ByteArray? {
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
            val complete = offset == 0L && (rangeHeader == null || (total != null && bytes.size.toLong() == total))
            cache.put(key, ProviderMediaStartupCache.Entry(bytes, total, proxy.contentType, complete, offset))
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
        const val BILIBILI_START_BITS_PER_SECOND = 1_000_000
    }
}
