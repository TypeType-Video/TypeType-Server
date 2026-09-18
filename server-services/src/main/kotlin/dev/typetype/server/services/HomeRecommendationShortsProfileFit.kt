package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationShortsProfileFit {
    fun score(video: VideoItem, profile: HomeRecommendationProfile): Double {
        val channelBoost = if (video.uploaderUrl in profile.subscriptionChannels) 0.18 else 0.0
        val watchLaterBoost = if (video.url in profile.watchLaterUrls) 0.12 else 0.0
        val keywordBoost = keywordBoost(video.title, profile.keywordAffinity)
        val topicBoost = topicBoost(video, profile.topicInterest)
        val delayedChannel = (profile.delayedChannelCredit[video.uploaderUrl] ?: 0.0).coerceIn(0.0, 2.0) * 0.06
        val delayedVideo = (profile.delayedVideoCredit[video.url] ?: 0.0).coerceIn(0.0, 2.0) * 0.05
        val genericPenalty = genericPenalty(video.title, profile)
        return channelBoost + watchLaterBoost + keywordBoost + topicBoost + delayedChannel + delayedVideo - genericPenalty
    }

    private fun keywordBoost(title: String, keywords: Set<String>): Double {
        if (keywords.isEmpty()) return 0.0
        val tokens = RecommendationTopicTokenizer.tokenize(title)
        if (tokens.isEmpty()) return 0.0
        val hits = tokens.count { it in keywords }
        return (hits.coerceAtMost(4)) * 0.06
    }

    private fun topicBoost(video: VideoItem, topicInterest: Map<String, Double>): Double {
        if (topicInterest.isEmpty()) return 0.0
        val tokens = RecommendationTopicTokenizer.tokenize("${video.title} ${video.uploaderName}")
        if (tokens.isEmpty()) return 0.0
        return tokens.sumOf { topicInterest[it] ?: 0.0 }.coerceIn(-3.0, 7.0) * 0.04
    }

    private fun genericPenalty(title: String, profile: HomeRecommendationProfile): Double {
        val tokens = RecommendationTopicTokenizer.tokenize(title)
        val generic = setOf("facts", "random", "viral", "dance", "trending", "funny")
        if (!tokens.any { it in generic }) return 0.0
        val overlap = tokens.count { it in profile.keywordAffinity || it in profile.topicInterest.keys }
        return if (overlap > 0) 0.03 else 0.08
    }
}
