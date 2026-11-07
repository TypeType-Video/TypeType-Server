package dev.typetype.server.services

import dev.typetype.server.models.CommentItem
import java.text.Normalizer
import java.util.Locale

class CommentSearchQuery private constructor(private val terms: List<String>) {
    fun matches(comment: CommentItem): Boolean {
        val haystack = normalize("${comment.author}\n${comment.text}")
        return terms.all { haystack.contains(it) }
    }

    companion object {
        const val MAX_LENGTH = 200
        private const val MAX_TERMS = 12
        private val TOKEN = Regex("\"([^\"]+)\"|(\\S+)")

        fun parse(raw: String?): CommentSearchQuery? {
            val text = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH } ?: return null
            val terms = TOKEN.findAll(text)
                .map { (it.groups[1]?.value ?: it.value).trim() }
                .map(::normalize)
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_TERMS)
                .toList()
            return terms.takeIf { it.isNotEmpty() }?.let(::CommentSearchQuery)
        }

        private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace('İ', 'i')
            .replace('I', 'i')
            .replace('ı', 'i')
            .lowercase(Locale.ROOT)
            .replace("i̇", "i")
            .replace(Regex("\\s+"), " ")
    }
}
