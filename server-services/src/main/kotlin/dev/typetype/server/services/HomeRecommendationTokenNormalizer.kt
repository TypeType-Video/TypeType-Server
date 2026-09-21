package dev.typetype.server.services

import java.text.Normalizer

object HomeRecommendationTokenNormalizer {
    private val combiningMarksRegex = Regex("\\p{M}+")
    private val nonAsciiWordRegex = Regex("[^a-z0-9]")

    fun normalize(token: String): String {
        if (token.isBlank()) return ""
        val decomposed = Normalizer.normalize(token.lowercase(), Normalizer.Form.NFD)
        return nonAsciiWordRegex.replace(combiningMarksRegex.replace(decomposed, ""), "")
    }
}
