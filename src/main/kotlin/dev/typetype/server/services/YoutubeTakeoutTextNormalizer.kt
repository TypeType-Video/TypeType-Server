package dev.typetype.server.services

import java.text.Normalizer
import java.util.Locale

object YoutubeTakeoutTextNormalizer {
    fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(MARKS_REGEX, "")
        .replace(NON_ALPHANUMERIC_REGEX, " ")
        .trim()

    private val MARKS_REGEX = Regex("\\p{M}+")
    private val NON_ALPHANUMERIC_REGEX = Regex("[^\\p{L}\\p{N}]+")
}
