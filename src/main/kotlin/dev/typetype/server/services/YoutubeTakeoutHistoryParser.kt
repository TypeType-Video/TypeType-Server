package dev.typetype.server.services

import dev.typetype.server.models.HistoryItem

object YoutubeTakeoutHistoryParser {
    private val videoLinkRegex = Regex(
        """<a\s+href=\"([^\"]*(?:youtube\.com/watch\?v=|youtube\.com/shorts/|youtube\.com/live/|youtu\.be/)[^\"]*)\"[^>]*>([^<]*)</a>""",
        RegexOption.IGNORE_CASE,
    )
    private val rowTailRegex = Regex("""\s*<br>\s*(?:<a href=\"([^\"]+)\">([^<]*)</a><br>\s*)?([^<]*)<br>""", RegexOption.IGNORE_CASE)
    private val urlRegex = Regex("""https?://(?:www\.)?(?:youtube\.com/(?:watch\?v=|shorts/|live/)|youtu\.be/)[A-Za-z0-9_-]{6,}""")
    private val tagRegex = Regex("<[^>]+>")
    private val spacesRegex = Regex("\\s+")

    fun parse(html: String, requireWatchedMarker: Boolean = true): List<HistoryItem> =
        parseWithDiagnostics(html, requireWatchedMarker).items

    internal fun parseWithDiagnostics(html: String, requireWatchedMarker: Boolean = true): YoutubeTakeoutHistoryParseResult {
        val resolvedHtml = html.replace("\u00a0", " ")
        var invalidDates = 0L
        val items = videoLinkRegex.findAll(resolvedHtml).mapNotNull { match ->
            val prefixStart = (match.range.first - WATCHED_PREFIX_CHARS).coerceAtLeast(0)
            if (requireWatchedMarker && !YoutubeTakeoutActivityClassifier.isWatched(
                    resolvedHtml.substring(prefixStart, match.range.first),
                )
            ) {
                return@mapNotNull null
            }
            val tailStart = match.range.last + 1
            val tail = rowTailRegex.find(resolvedHtml, tailStart)
                ?.takeIf { it.range.first == tailStart }
                ?: return@mapNotNull null
            val url = extractUrl(match.groupValues[1]) ?: return@mapNotNull null
            val title = decode(match.groupValues[2])
            if (YoutubeTakeoutUnavailableItem.matches(title)) return@mapNotNull null
            val channelUrl = tail.groupValues[1].takeIf { it.isNotBlank() }.orEmpty()
            val channelName = decode(tail.groupValues[2]).ifBlank { "Unknown channel" }
            val dateText = decode(tail.groupValues[3])
            val watchedAt = parseDate(dateText) ?: run {
                invalidDates += 1
                return@mapNotNull null
            }
            HistoryItem(
                url = url,
                title = title,
                thumbnail = "",
                channelName = channelName,
                channelUrl = channelUrl,
                channelAvatar = "",
                duration = 0,
                progress = 0,
                watchedAt = watchedAt,
            )
        }.toList().distinctBy { it.url to it.watchedAt }
        return YoutubeTakeoutHistoryParseResult(items, invalidDates)
    }

    private fun parseDate(value: String): Long? = YoutubeTakeoutDateParser.parseEpochMillis(value)

    private fun extractUrl(value: String): String? {
        val decoded = decode(value)
        val direct = urlRegex.find(decoded)?.value
        if (direct != null) return direct
        val fromText = urlRegex.find(decode(tagRegex.replace(decoded, " ")))?.value
        return fromText
    }

    private fun decode(value: String): String = tagRegex.replace(value, " ")
        .replace("&nbsp;", " ")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(spacesRegex, " ")
        .trim()

    private const val WATCHED_PREFIX_CHARS = 160
}

internal data class YoutubeTakeoutHistoryParseResult(
    val items: List<HistoryItem>,
    val invalidDates: Long,
)
