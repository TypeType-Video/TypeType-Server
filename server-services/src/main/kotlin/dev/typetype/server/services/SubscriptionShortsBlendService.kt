package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem

class SubscriptionShortsBlendService(
    private val trendingService: TrendingService,
) {
    suspend fun build(
        subs: List<VideoItem>,
        serviceId: Int,
        page: Int,
        limit: Int,
    ): SubscriptionFeedResponse {
        val discovery = fetchTrendingShorts(serviceId)
            .map { it.toShortCanonicalUrl() }
            .filter { video -> subs.none { it.toShortDedupKey() == video.toShortDedupKey() } }
            .distinctBy { it.url }
        val rankedSubs = subs.sortedByDescending { scoreShort(it, true) }
        val rankedDiscovery = discovery.sortedByDescending { scoreShort(it, false) }
        val discoveryPage = rankedDiscovery.drop(page * limit).take(limit)
        val videos = blend(rankedSubs, discoveryPage, limit)
        val hasNext = hasMoreSubs(subs, limit) || hasMoreDiscovery(rankedDiscovery, page, limit)
        return SubscriptionFeedResponse(videos = videos, nextpage = if (hasNext) (page + 1).toString() else null)
    }

    private suspend fun fetchTrendingShorts(serviceId: Int): List<VideoItem> =
        when (val trending = trendingService.getTrending(serviceId)) {
            is ExtractionResult.Success -> trending.data
            is ExtractionResult.BadRequest -> emptyList()
            is ExtractionResult.Failure -> emptyList()
        }.filter { it.isShortFormContent || it.duration in 1..60 }

    private fun blend(subs: List<VideoItem>, discovery: List<VideoItem>, limit: Int): List<VideoItem> {
        val subscriptionUrls = subs.map { it.uploaderUrl }.filter { it.isNotBlank() }.toSet()
        val result = mutableListOf<VideoItem>()
        var si = 0
        var di = 0
        val subQuota = targetSubscriptionQuota(limit, subs.isNotEmpty())
        while (result.size < limit && (si < subs.size || di < discovery.size)) {
            val currentSubCount = result.count { it.uploaderUrl in subscriptionUrls }
            if (si < subs.size && currentSubCount < subQuota) result += subs[si++]
            if (result.size < limit && di < discovery.size) result += discovery[di++]
            if (si < subs.size && di >= discovery.size && result.size < limit) result += subs[si++]
        }
        return result.distinctBy { it.url }.take(limit)
    }

    private fun targetSubscriptionQuota(limit: Int, hasSubs: Boolean): Int {
        if (!hasSubs) return 0
        return (limit * 0.6).toInt().coerceAtLeast(1)
    }

    private fun hasMoreSubs(subs: List<VideoItem>, limit: Int): Boolean = subs.size >= limit

    private fun hasMoreDiscovery(discovery: List<VideoItem>, page: Int, limit: Int): Boolean {
        val nextStart = (page + 1) * limit
        return nextStart < discovery.size
    }

    private fun scoreShort(video: VideoItem, isSubscription: Boolean): Double {
        val ageBoost = if (video.uploaded <= 0L) 0.0 else 1.0 / (1.0 + ((System.currentTimeMillis() - video.uploaded).coerceAtLeast(0L) / 3_600_000.0) / 24.0)
        val sourceBoost = if (isSubscription) 1.0 else 0.85
        return sourceBoost + ageBoost
    }
}
