package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationShortsHistorySignals {
    fun promote(
        profile: HomeRecommendationProfile,
        subscriptions: List<HomeRecommendationTaggedVideo>,
    ): List<HomeRecommendationTaggedVideo> {
        val keywords = profile.keywordAffinity.take(20)
        if (keywords.isEmpty()) return subscriptions
        return subscriptions.map { tagged ->
            if (matches(tagged.video, keywords)) {
                tagged.copy(source = HomeRecommendationSourceTag.DISCOVERY_THEME)
            } else {
                tagged
            }
        }
    }

    private fun matches(video: VideoItem, keywords: List<String>): Boolean {
        val text = "${video.title} ${video.uploaderName}".lowercase()
        return keywords.any { text.contains(it.lowercase()) }
    }
}
