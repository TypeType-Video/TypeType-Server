package dev.typetype.server.services

object HomeRecommendationShortsDeduplicator {
    fun apply(
        candidates: List<HomeRecommendationTaggedVideo>,
        historyUrls: List<String>,
        subscriptionChannels: List<String>,
    ): List<HomeRecommendationTaggedVideo> {
        if (candidates.size <= 1) return candidates
        val historyIds = historyUrls.mapNotNull { extractYouTubeVideoId(it) }.toSet()
        val seenSemantic = mutableSetOf<String>()
        val seenTitleTokens = mutableListOf<Set<String>>()
        val normalizedSubs = subscriptionChannels.filter { it.isNotBlank() }.toSet()
        val result = mutableListOf<HomeRecommendationTaggedVideo>()
        candidates.forEach { tagged ->
            val video = tagged.video
            val id = extractYouTubeVideoId(video.url)
            if (id != null && id in historyIds) return@forEach
            val semanticKey = HomeRecommendationSemanticKey.fromTitle(video.title)
            val titleTokens = RecommendationTopicTokenizer.tokenize(video.title)
            val isNearDuplicate = seenTitleTokens.any { previous -> similarity(previous, titleTokens) >= 0.60 }
            if (semanticKey.isNotBlank() && semanticKey in seenSemantic) return@forEach
            if (titleTokens.isNotEmpty() && isNearDuplicate) return@forEach
            if (video.uploaderUrl in normalizedSubs && result.count { it.video.uploaderUrl == video.uploaderUrl } >= 2) return@forEach
            if (semanticKey.isNotBlank()) seenSemantic += semanticKey
            if (titleTokens.isNotEmpty()) seenTitleTokens += titleTokens
            result += tagged
        }
        return result
    }

    private fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val union = a.union(b).size
        if (union == 0) return 0.0
        return a.intersect(b).size.toDouble() / union.toDouble()
    }

    private fun extractYouTubeVideoId(url: String): String? {
        val shortsMarker = "/shorts/"
        val shortsStart = url.indexOf(shortsMarker)
        if (shortsStart != -1) {
            val value = url.substring(shortsStart + shortsMarker.length)
            return value.substringBefore('?').substringBefore('&').substringBefore('#').takeIf { it.isNotBlank() }
        }
        val shortHostMarker = "youtu.be/"
        val shortHostStart = url.indexOf(shortHostMarker)
        if (shortHostStart != -1) {
            val value = url.substring(shortHostStart + shortHostMarker.length)
            return value.substringBefore('?').substringBefore('&').substringBefore('#').takeIf { it.isNotBlank() }
        }
        val marker = "v="
        val start = url.indexOf(marker)
        if (start == -1) return null
        val value = url.substring(start + marker.length)
        return value.substringBefore('&').substringBefore('#').takeIf { it.isNotBlank() }
    }
}
