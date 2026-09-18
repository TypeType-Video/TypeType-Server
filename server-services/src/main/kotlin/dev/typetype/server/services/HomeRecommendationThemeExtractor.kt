package dev.typetype.server.services

import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.WatchLaterItem

object HomeRecommendationThemeExtractor {
    private val genericTerms = setOf(
        "topic", "technopolis", "podcast", "podcasts", "official", "gaming", "vlog", "music", "mix", "live",
    )
    private val stopWords = setOf(
        "the", "and", "for", "with", "official", "channel", "tv", "video", "videos", "vlog", "vlogs", "new", "best",
        "top", "how", "what", "when", "where", "why", "this", "that", "these", "those", "from", "about", "more",
        "sur", "les", "des", "une", "pour", "avec", "dans", "par", "est", "une", "que", "qui", "plus", "tout",
    )

    fun extractThemeTokens(
        subscriptions: List<SubscriptionItem>,
        watchLater: List<WatchLaterItem>,
    ): Set<String> {
        val tokens = mutableListOf<String>()
        subscriptions.forEach { sub ->
            tokens.addAll(tokenize(sub.name))
        }
        watchLater.forEach { wl ->
            tokens.addAll(tokenize(wl.title))
        }
        return tokens
            .groupingBy { it }
            .eachCount()
            .filterKeys { it !in stopWords }
            .filterKeys { it.length >= 3 }
            .entries
            .sortedByDescending { it.value }
            .take(100)
            .map { it.key }
            .toSet()
    }

    fun computeThemeScore(videoTitle: String, channelName: String, themeTokens: Set<String>): Double {
        if (themeTokens.isEmpty()) return 0.5
        val videoTokens = tokenize(videoTitle).toSet()
        val channelTokens = tokenize(channelName).toSet()
        val titleMatches = videoTokens.count { it in themeTokens }
        val channelMatches = channelTokens.count { it in themeTokens }
        val titleScore = if (videoTokens.isNotEmpty()) titleMatches.toDouble() / videoTokens.size else 0.0
        val channelScore = if (channelTokens.isNotEmpty()) channelMatches.toDouble() / channelTokens.size else 0.0
        return (titleScore * 0.7 + channelScore * 0.3).coerceIn(0.0, 1.0)
    }

    fun buildThemeQueries(themeTokens: Set<String>): List<String> {
        if (themeTokens.isEmpty()) return emptyList()
        val ranked = themeTokens
            .filterNot { token -> token in genericTerms }
            .sortedByDescending { token -> token.length }
            .take(30)
        if (ranked.isEmpty()) return emptyList()
        val queries = mutableSetOf<String>()
        for (window in 0 until ranked.size step 3) {
            val chunk = ranked.drop(window).take(3)
            if (chunk.isEmpty()) continue
            queries += chunk.joinToString(" ")
            queries += chunk.first()
        }
        return queries.take(12)
    }

    private fun tokenize(text: String): List<String> =
        text
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .map { HomeRecommendationTokenNormalizer.normalize(it) }
            .filter { it.isNotEmpty() }
}
