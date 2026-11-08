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
    private val probeMirrors: (urls: List<String>, totalLength: Long?) -> Unit = { _, _ -> },
    private val hlsChain: HlsSegmentChain = HlsSegmentChain(),
) {
    private class Media(
        val url: String,
        val indexStart: Long,
        val indexEnd: Long,
        val startsPlayback: Boolean,
        val bitrate: Int?,
        val isVideo: Boolean,
    )

    private val permits = Semaphore(MAX_PARALLEL_FETCHES)
    private val start = DashStartFetch(proxyService, cache)

    fun warm(response: StreamResponse, provider: ProviderMediaType): Job =
        warm(provider, response.videoOnlyStreams + response.videoStreams, response.audioStreams)

    fun warm(provider: ProviderMediaType, video: List<VideoStreamItem>, audio: List<AudioStreamItem>): Job =
        scope.launch {
            withTimeoutOrNull(WARMUP_TIMEOUT_MILLIS) {
                val starting = ProviderMediaStartTracks.urls(provider, video, audio)
                val media = video.map { Media(it.url, it.indexStart, it.indexEnd, it.url in starting, it.bitrate, isVideo = true) } +
                    audio.map { Media(it.url, it.indexStart, it.indexEnd, it.url in starting, it.bitrate, isVideo = false) }
                when (provider) {
                    ProviderMediaType.BILIBILI -> coroutineScope {
                        media.map { item -> async { targetOf(item.url)?.let { warmDash(it, item) } } }.awaitAll()
                    }
                    ProviderMediaType.NICONICO -> warmHls(media, video.size)
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
        if (media.startsPlayback && warmDashStart(target, media)) return
        val header = prefetch(target, "bytes=0-${media.indexEnd}", MAX_DASH_HEADER_BYTES) ?: return
        val table = Mp4SegmentIndex.segments(header, media.indexStart) ?: return
        segments.remember(target.url, table)
        if (!media.startsPlayback) return
        if (media.isVideo) probeMirrors(listOf(target.url) + target.alternateUrls, cache.get(target.url)?.totalLength)
        val end = table.first().last
        if (end < header.size || end >= MAX_SEGMENT_BYTES) return
        val claim = cache.begin(target.url, extending = true) ?: return
        fetchClaimed(target, "bytes=0-$end", MAX_SEGMENT_BYTES, claim)
    }

    private suspend fun warmDashStart(target: ProviderMediaTarget, media: Media): Boolean = permits.withPermit {
        start.fetch(target, media.indexStart, media.indexEnd, media.bitrate) { header ->
            header.segments?.let { segments.remember(target.url, it) }
            if (media.isVideo) probeMirrors(listOf(target.url) + target.alternateUrls, header.totalLength)
        }
    }

    fun prefetchFollowing(handle: String): Job? {
        val following = hlsChain.following(handle).takeIf { it.isNotEmpty() } ?: return null
        if (permits.availablePermits == 0) return null
        return scope.launch {
            following.map { file -> async { targetOf(file)?.let { prefetch(it, null, MAX_SEGMENT_BYTES) } } }.awaitAll()
        }
    }

    private suspend fun warmHls(media: List<Media>, videoCount: Int) = coroutineScope {
        val playlists = media.map { item ->
            async {
                val target = targetOf(item.url) ?: return@async null
                prefetch(target, null, MAX_PLAYLIST_BYTES)?.toString(StandardCharsets.UTF_8)
                    ?.let(HlsWarmupPlan::fromMediaPlaylist)
                    ?.let { it to item.startsPlayback }
            }
        }.awaitAll()
        hlsChain.remember(
            playlists.take(videoCount).mapNotNull { it?.first?.segments },
            playlists.getOrNull(videoCount)?.first?.segments,
        )
        val files = playlists.filterNotNull().flatMap { (playlist, startsPlayback) ->
            playlist.headers + listOfNotNull(playlist.firstSegment.takeIf { startsPlayback })
        }.distinct()
        files.map { file -> async { targetOf(file)?.let { prefetch(it, null, MAX_SEGMENT_BYTES) } } }.awaitAll()
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
        const val MAX_PARALLEL_FETCHES = 16
        const val MAX_DASH_HEADER_BYTES = 64 * 1024
        const val MAX_PLAYLIST_BYTES = 512 * 1024
        const val MAX_SEGMENT_BYTES = 4 * 1024 * 1024
        const val WARMUP_TIMEOUT_MILLIS = 20_000L
    }
}
