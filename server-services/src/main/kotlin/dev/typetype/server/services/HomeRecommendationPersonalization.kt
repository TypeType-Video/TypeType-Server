package dev.typetype.server.services

import dev.typetype.server.models.VideoItem
import kotlin.math.ln
import kotlin.math.sqrt

object HomeRecommendationPersonalization {
    fun viralVelocityBoost(video: VideoItem): Double {
        if (video.viewCount <= 0 || video.uploaded <= 0L) return 0.0
        val hoursOld = ((System.currentTimeMillis() - video.uploaded).coerceAtLeast(3_600_000L)) / 3_600_000.0
        val viewsPerHour = video.viewCount.toDouble() / hoursOld
        return (ln(1.0 + viewsPerHour) / ln(10.0) * 0.03).coerceIn(0.0, 0.18)
    }

    fun curiosityBoost(video: VideoItem, profile: HomeRecommendationProfile): Double {
        val tokens = RecommendationTopicTokenizer.tokenize(video.title)
        if (tokens.isEmpty()) return 0.0
        val topicScores = tokens.mapNotNull { profile.topicInterest[it] }
        if (topicScores.isEmpty()) return 0.0
        val avg = topicScores.average()
        val std = sqrt(topicScores.map { (it - avg) * (it - avg) }.average())
        return (std * 0.03).coerceIn(0.0, 0.12)
    }

    fun serendipityBoost(video: VideoItem, profile: HomeRecommendationProfile): Double {
        val tokens = RecommendationTopicTokenizer.tokenize("${video.title} ${video.uploaderName}")
        if (tokens.isEmpty()) return 0.0
        val matched = tokens.count { it in profile.topicInterest }
        val novelty = (tokens.size - matched).coerceAtLeast(0).toDouble() / tokens.size.toDouble()
        return (novelty * 0.08).coerceIn(0.0, 0.08)
    }

    fun channelProfileBoost(video: VideoItem, profile: HomeRecommendationProfile): Double {
        val channelTopics = profile.channelTopicProfile[video.uploaderUrl] ?: return 0.0
        if (channelTopics.isEmpty()) return 0.0
        val tokens = RecommendationTopicTokenizer.tokenize(video.title)
        if (tokens.isEmpty()) return 0.0
        val raw = tokens.sumOf { channelTopics[it] ?: 0.0 }
        return (raw * 0.08).coerceIn(0.0, 0.16)
    }

    fun applyPenalties(video: VideoItem, score: Double, profile: HomeRecommendationProfile): Double {
        val feedPenalty = HomeRecommendationFeedHistoryPenalty.penalty(profile.feedHistory[video.url])
        val channelPenalty = profile.rejectionChannelPenalty[video.uploaderUrl].orDefault()
        val topicPenalty = topicPenalty(video, profile.rejectionTopicPenalty)
        val topicPairPenalty = topicPairPenalty(video, profile.rejectionTopicPairPenalty)
        val shortTopicBoost = shortTopicBoost(video, profile.shortsTopicInterest)
        return (score * feedPenalty * channelPenalty * topicPenalty * topicPairPenalty) + shortTopicBoost
    }

    private fun topicPenalty(video: VideoItem, penalties: Map<String, Double>): Double {
        if (penalties.isEmpty()) return 1.0
        val tokens = RecommendationTopicTokenizer.tokenize(video.title)
        if (tokens.isEmpty()) return 1.0
        return tokens.mapNotNull { penalties[it] }.minOrNull().orDefault()
    }

    private fun shortTopicBoost(video: VideoItem, shortsTopicInterest: Map<String, Double>): Double {
        if (!video.isShortFormContent || shortsTopicInterest.isEmpty()) return 0.0
        val tokens = RecommendationTopicTokenizer.tokenize(video.title)
        if (tokens.isEmpty()) return 0.0
        val score = tokens.sumOf { shortsTopicInterest[it] ?: 0.0 }
        return (score * 0.04).coerceIn(-0.4, 0.4)
    }

    private fun topicPairPenalty(video: VideoItem, penalties: Map<String, Double>): Double {
        if (penalties.isEmpty()) return 1.0
        val pairs = HomeRecommendationTopicPairs.fromTitle(video.title)
        if (pairs.isEmpty()) return 1.0
        return pairs.mapNotNull { penalties[it] }.minOrNull().orDefault()
    }

    private fun Double?.orDefault(default: Double = 1.0): Double = this ?: default
}
