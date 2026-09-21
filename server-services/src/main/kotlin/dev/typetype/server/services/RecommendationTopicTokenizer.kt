package dev.typetype.server.services

object RecommendationTopicTokenizer {
    private val stopWords = setOf(
        "the", "and", "for", "with", "this", "that", "from", "your", "into", "about", "how", "what", "when",
        "sur", "les", "des", "une", "pour", "avec", "dans", "que", "qui", "est", "tout", "plus", "sans",
    )

    fun tokenize(text: String): Set<String> = text
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .asSequence()
        .map { HomeRecommendationTokenNormalizer.normalize(it) }
        .filter { token -> token.length >= 3 && token !in stopWords }
        .toSet()
}
