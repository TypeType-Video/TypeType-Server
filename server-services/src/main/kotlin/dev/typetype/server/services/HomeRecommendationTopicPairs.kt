package dev.typetype.server.services

object HomeRecommendationTopicPairs {
    fun fromTitle(title: String): List<String> {
        val tokens = RecommendationTopicTokenizer.tokenize(title).sorted().take(4)
        if (tokens.size < 2) return emptyList()
        val pairs = mutableListOf<String>()
        for (i in tokens.indices) {
            for (j in (i + 1) until tokens.size) {
                pairs += "${tokens[i]}|${tokens[j]}"
            }
        }
        return pairs
    }
}
