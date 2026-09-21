package dev.typetype.server.services

object HomeRecommendationSemanticKey {
    fun fromTitle(title: String): String {
        val tokens = title.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .asSequence()
            .filter { it.length >= 4 }
            .distinct()
            .take(3)
            .toList()
        return tokens.joinToString("|")
    }
}
