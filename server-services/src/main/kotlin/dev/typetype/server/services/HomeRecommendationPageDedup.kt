package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationPageDedup {
    fun acrossPages(items: List<VideoItem>, cursor: HomeRecommendationCursor): List<VideoItem> {
        if (items.size <= 1) return items
        val seenUrls = cursor.recentSemanticKeys.toSet()
        val deduped = mutableListOf<VideoItem>()
        val localSeenUrls = mutableSetOf<String>()
        items.forEach { video ->
            if (video.url in localSeenUrls) return@forEach
            if (video.url in seenUrls) return@forEach
            localSeenUrls += video.url
            deduped += video
        }
        return deduped
    }
}
