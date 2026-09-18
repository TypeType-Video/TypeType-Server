package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationLanguageGate {
    fun isLikelyPreferred(video: VideoItem, profile: HomeRecommendationProfile): Boolean {
        if (profile.themeTokens.isEmpty()) return true
        val text = "${video.title} ${video.uploaderName}".lowercase()
        val alpha = text.count { it in 'a'..'z' }
        val latinRatio = alpha.toDouble() / text.length.coerceAtLeast(1)
        if (latinRatio < 0.2) return false
        val hits = profile.themeTokens.count { token -> token in text }
        return hits >= 1
    }
}
