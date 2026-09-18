package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem

class HomeRecommendationPicker(
    private val pool: HomeRecommendationPool,
    private val channelCount: Map<String, Int>,
    private val recentChannels: Set<String>,
    private val recentSemanticKeys: Set<String>,
    private val creatorMomentum: Map<String, Int>,
    private val creatorCooldownUntilMs: Map<String, Long>,
    private val recentTopicPairs: Set<String>,
    private val recentUrls: Set<String>,
) {
    private val subscriptionUrls: Set<String> = pool.subscriptions.map { it.url }.toSet()

    fun fromDiscovery(start: Int, noveltyOnly: Boolean = false): Pair<VideoItem?, Int> =
        pick(pool.discovery, start, noveltyOnly)

    fun fromDiscoveryRelaxed(start: Int): Pair<VideoItem?, Int> = pickDiscoveryRelaxed(start)

    fun fromSubscriptions(start: Int): Pair<VideoItem?, Int> = pick(pool.subscriptions, start, false)

    fun sourceOf(video: VideoItem): HomeRecommendationSourceTag =
        pool.sourceByUrl[video.url]
            ?: if (video.url in subscriptionUrls) HomeRecommendationSourceTag.SUBSCRIPTION
            else HomeRecommendationSourceTag.DISCOVERY_EXPLORATION

    private fun pick(source: List<VideoItem>, start: Int, noveltyOnly: Boolean): Pair<VideoItem?, Int> {
        var index = start
        while (index < source.size) {
            val candidate = source[index]
            index += 1
            if (candidate.url in recentUrls) continue
            if (noveltyOnly && !isNovel(candidate)) continue
            val channel = channelKey(candidate)
            if (channel.isBlank()) return candidate to index
            val count = channelCount[channel] ?: 0
            if (count >= MAX_PER_CHANNEL_PER_PAGE) continue
            if (channel in recentChannels) continue
            if (isCoolingDown(channel)) continue
            if ((creatorMomentum[channel] ?: 0) >= MAX_CREATOR_MOMENTUM_PICK) continue
            val semanticKey = HomeRecommendationSemanticKey.fromTitle(candidate.title)
            if (semanticKey.isNotBlank() && semanticKey in recentSemanticKeys) continue
            val pairs = HomeRecommendationTopicPairs.fromTitle(candidate.title)
            if (pairs.isNotEmpty() && pairs.any { it in recentTopicPairs }) continue
            return candidate to index
        }
        return null to index
    }

    private fun pickDiscoveryRelaxed(start: Int): Pair<VideoItem?, Int> {
        var index = start
        while (index < pool.discovery.size) {
            val candidate = pool.discovery[index]
            index += 1
            val channel = channelKey(candidate)
            if (channel.isNotBlank()) {
                val count = channelCount[channel] ?: 0
                if (count >= MAX_PER_CHANNEL_PER_PAGE) continue
            }
            return candidate to index
        }
        return null to index
    }

    private fun isCoolingDown(channel: String): Boolean {
        val until = creatorCooldownUntilMs[channel] ?: return false
        return until > System.currentTimeMillis()
    }

    private fun channelKey(video: VideoItem): String = video.uploaderUrl.ifBlank { video.uploaderName }

    private fun isNovel(video: VideoItem): Boolean {
        val semanticKey = HomeRecommendationSemanticKey.fromTitle(video.title)
        return semanticKey.isNotBlank() && semanticKey !in recentSemanticKeys
    }

    companion object {
        private const val MAX_PER_CHANNEL_PER_PAGE = 2
        private const val MAX_CREATOR_MOMENTUM_PICK = 4
    }
}
