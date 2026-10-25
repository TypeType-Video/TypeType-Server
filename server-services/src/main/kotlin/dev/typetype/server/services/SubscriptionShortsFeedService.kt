package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer

class SubscriptionShortsFeedService(
    private val subscriptionsService: SubscriptionsService,
    channelService: ChannelService,
    private val blendService: SubscriptionShortsBlendService,
    private val cache: CacheService,
    private val channelVideos: ChannelVideosSource = ChannelServiceVideosSource(channelService),
) {
    private val semaphore = Semaphore(20)

    suspend fun getFeed(userId: String, page: Int, limit: Int): SubscriptionFeedResponse {
        val all = cachedAll(userId)
        val from = page * limit
        if (from >= all.size) return SubscriptionFeedResponse(videos = emptyList(), nextpage = null)
        val to = minOf(from + limit, all.size)
        val next = if (to < all.size) (page + 1).toString() else null
        return SubscriptionFeedResponse(videos = all.subList(from, to), nextpage = next)
    }
    suspend fun getCachedFeed(userId: String, page: Int, limit: Int): SubscriptionFeedResponse? {
        val raw = runCatching { cache.get(SubscriptionFeedCacheKeys.shorts(userId)) }.getOrNull() ?: return null
        val all = runCatching { CacheJson.decodeFromString(ListSerializer(VideoItem.serializer()), raw) }.getOrNull()
            ?: return null
        val from = page * limit
        if (from >= all.size) return SubscriptionFeedResponse(videos = emptyList(), nextpage = null)
        val to = minOf(from + limit, all.size)
        val next = if (to < all.size) (page + 1).toString() else null
        return SubscriptionFeedResponse(videos = all.subList(from, to), nextpage = next)
    }
    suspend fun getBlendedFeed(userId: String, serviceId: Int, page: Int, limit: Int): SubscriptionFeedResponse {
        val sourcePage = page
        val subs = getFeed(userId, sourcePage, limit).videos
        return blendService.build(subs = subs, serviceId = serviceId, page = page, limit = limit)
    }
    private suspend fun cachedAll(userId: String): List<VideoItem> {
        val key = SubscriptionFeedCacheKeys.shorts(userId)
        runCatching { cache.get(key) }.getOrNull()?.let { raw ->
            return runCatching { CacheJson.decodeFromString(ListSerializer(VideoItem.serializer()), raw) }
                .getOrElse { fetchAndCache(userId, key) }
        }
        return fetchAndCache(userId, key)
    }
    private suspend fun fetchAndCache(userId: String, key: String): List<VideoItem> {
        val subs = subscriptionsService.getAll(userId)
        val sources = coroutineScope {
            subs.map { sub ->
                async {
                    semaphore.withPermit {
                        fetchForSubscription(sub.channelUrl)
                    }
                }
            }.map { it.await() }
        }
        val dedup = sources.flatMap(SourceVideos::videos).distinctBy { it.toShortDedupKey() }
            .filter { it.isShortFormContent }
            .map { it.toShortCanonicalUrl() }
            .sortedByDescending { if (it.uploaded == -1L) Long.MIN_VALUE else it.uploaded }
            .let { diversifyByUploader(it) }
        val failed = sources.count { !it.success }
        val ttlSeconds = when {
            failed == 0 -> FEED_TTL_SECONDS
            failed == sources.size -> ALL_FAILED_TTL_SECONDS
            else -> PARTIAL_TTL_SECONDS
        }
        runCatching { cache.set(key, CacheJson.encodeToString(ListSerializer(VideoItem.serializer()), dedup), ttlSeconds) }
        return dedup
    }
    private fun diversifyByUploader(videos: List<VideoItem>): List<VideoItem> {
        val grouped = videos.groupBy { uploaderKey(it) }
        val queues = grouped.values
            .map { group -> ArrayDeque(group.sortedByDescending { if (it.uploaded == -1L) Long.MIN_VALUE else it.uploaded }) }
            .sortedByDescending { queue -> queue.firstOrNull()?.let { if (it.uploaded == -1L) Long.MIN_VALUE else it.uploaded } ?: Long.MIN_VALUE }
            .toMutableList()
        val result = ArrayList<VideoItem>(videos.size)
        while (queues.isNotEmpty()) {
            val nextRound = mutableListOf<ArrayDeque<VideoItem>>()
            queues.forEach { queue ->
                queue.removeFirstOrNull()?.let { result += it }
                if (queue.isNotEmpty()) nextRound += queue
            }
            queues.clear()
            queues.addAll(nextRound)
        }
        return result
    }

    private fun uploaderKey(video: VideoItem): String = video.uploaderUrl.ifBlank { video.uploaderName }

    private suspend fun fetchForSubscription(channelUrl: String): SourceVideos {
        if (recommendationServiceId(channelUrl) == YOUTUBE_SERVICE_ID) {
            val shorts = fetch(shortsTabUrl(channelUrl))
            if (shorts.videos.isNotEmpty()) return shorts.copy(videos = shorts.videos.map { it.copy(isShortFormContent = true) })
        }
        return fetch(channelUrl)
    }

    private suspend fun fetch(url: String): SourceVideos = try {
        when (val result = withTimeoutOrNull(FETCH_TIMEOUT_MS) { channelVideos.firstPage(url) }) {
            is ExtractionResult.Success -> SourceVideos(result.data.videos, success = true)
            else -> SourceVideos(emptyList(), success = false)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        SourceVideos(emptyList(), success = false)
    }

    private fun shortsTabUrl(channelUrl: String): String {
        if (channelUrl.endsWith("/shorts")) return channelUrl
        return if (channelUrl.endsWith('/')) "${channelUrl}shorts" else "$channelUrl/shorts"
    }

    private data class SourceVideos(val videos: List<VideoItem>, val success: Boolean)

    companion object {
        private const val FEED_TTL_SECONDS = 3_600L
        private const val PARTIAL_TTL_SECONDS = 300L
        private const val ALL_FAILED_TTL_SECONDS = 30L
        private const val FETCH_TIMEOUT_MS = 15_000L
    }
}
