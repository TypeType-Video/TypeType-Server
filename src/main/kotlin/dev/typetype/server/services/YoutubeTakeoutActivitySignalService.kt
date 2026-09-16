package dev.typetype.server.services

import dev.typetype.server.models.FavoriteItem
import dev.typetype.server.models.SubscriptionItem
import java.nio.file.Path
import java.util.zip.ZipFile

object YoutubeTakeoutActivitySignalService {
    private val activityLinkRegex = Regex("""<a\s+href=\"([^\"]+)\"[^>]*>([^<]*)</a>""", RegexOption.IGNORE_CASE)
    private val rowTailRegex = Regex("""\s*<br>\s*(?:<a href=\"([^\"]+)\">([^<]*)</a><br>\s*)?([^<]*)<br>""", RegexOption.IGNORE_CASE)
    private val watchUrlRegex = Regex("""https?://(?:www\.)?(?:youtube\.com/(?:watch\?v=|shorts/)|youtu\.be/)[A-Za-z0-9_-]{6,}""")
    private val channelUrlRegex = Regex("""https?://www\.youtube\.com/(?:channel/[A-Za-z0-9_-]+|@[A-Za-z0-9._-]+)""")
    private val spacesRegex = Regex("""\s+""")

    fun parse(zipPath: Path): Pair<List<SubscriptionItem>, List<FavoriteItem>> {
        val subscriptions = mutableListOf<SubscriptionItem>()
        val favorites = mutableListOf<FavoriteItem>()
        ZipFile(zipPath.toFile()).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory || !YoutubeTakeoutPathHints.isYoutubeHtml(entry.name) || isHistoryEntry(entry.name)) return@forEach
                val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }.replace("\u00a0", " ")
                val parsed = parseHtml(html)
                subscriptions += parsed.first
                favorites += parsed.second
            }
        }
        return subscriptions.distinctBy { it.channelUrl } to favorites.distinctBy { it.videoUrl }
    }

    internal fun parseHtml(html: String): Pair<List<SubscriptionItem>, List<FavoriteItem>> =
        parseLinks(html)

    private fun parseLinks(html: String): Pair<List<SubscriptionItem>, List<FavoriteItem>> {
        val resolvedHtml = html.replace("\u00a0", " ")
        val subscriptions = mutableListOf<SubscriptionItem>()
        val favorites = mutableListOf<FavoriteItem>()
        activityLinkRegex.findAll(resolvedHtml).forEach { match ->
            val prefixStart = (match.range.first - PREFIX_CHARS).coerceAtLeast(0)
            val prefix = resolvedHtml.substring(prefixStart, match.range.first)
            val href = decode(match.groupValues[1])
            val text = decode(match.groupValues[2])
            channelUrlRegex.find(href)?.value?.let { channelUrl ->
                if (YoutubeTakeoutActivityClassifier.isSubscribed(prefix)) {
                    subscriptions += SubscriptionItem(channelUrl.replace("http://", "https://"), text, "")
                }
            }
            val videoUrl = watchUrlRegex.find(href)?.value?.replace("http://", "https://") ?: return@forEach
            if (!YoutubeTakeoutActivityClassifier.isLiked(prefix) || YoutubeTakeoutUnavailableItem.matches(text)) return@forEach
            val tailStart = match.range.last + 1
            val tail = rowTailRegex.find(resolvedHtml, tailStart)?.takeIf { it.range.first == tailStart } ?: return@forEach
            val favoritedAt = YoutubeTakeoutDateParser.parseEpochMillis(decode(tail.groupValues[3]))
                ?: return@forEach
            favorites += FavoriteItem(
                videoUrl = videoUrl,
                favoritedAt = favoritedAt,
                title = text,
                channelName = decode(tail.groupValues[2]),
                channelUrl = decode(tail.groupValues[1]).replace("http://", "https://"),
            )
        }
        return subscriptions to favorites
    }

    private fun decode(value: String): String {
        return value
            .replace("&nbsp;", " ")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(spacesRegex, " ")
            .trim()
    }

    private fun isHistoryEntry(name: String): Boolean = YoutubeTakeoutPathHints.isHistoryEntry(name)

    private const val PREFIX_CHARS = 160
}
