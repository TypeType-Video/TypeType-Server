package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import dev.typetype.server.services.YoutubeTakeoutActivityClassifier
import dev.typetype.server.services.YoutubeTakeoutDateParser
import dev.typetype.server.services.YoutubeTakeoutPathHints
import dev.typetype.server.services.YoutubeTakeoutTextNormalizer
import java.io.InputStream

internal object YoutubeTakeoutJsonPortabilityReader {
    private val videoUrlRegex = Regex(
        """(?:https?://|URLs://)(?:www\.|music\.)?youtube\.com/(?:watch\?(?:[^#\s]*&)?v=|shorts/|live/)[A-Za-z0-9_-]{6,}|https?://youtu\.be/[A-Za-z0-9_-]{6,}""",
        RegexOption.IGNORE_CASE,
    )
    private val channelUrlRegex = Regex(
        """https?://(?:www\.)?youtube\.com/(?:channel/[A-Za-z0-9_-]+|@[A-Za-z0-9._-]+|c/[A-Za-z0-9._-]+|user/[A-Za-z0-9._-]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val actionPrefixRegex = Regex(
        """^(?:you\s+watched|watched|viewed|you\s+liked|liked|has\s+visto|vous\s+avez\s+regard[eé]|vous\s+avez\s+aime[eé]|te\s+ha\s+gustado|izl[eə]nildi)(?:\s+|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val actionSuffixRegex = Regex(
        """\s+(?:izl[eə]nildi|を視聴しました|を再生しました)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun isCandidate(path: String): Boolean {
        if (!path.endsWith(".json", ignoreCase = true)) return false
        val normalized = YoutubeTakeoutTextNormalizer.normalize(path)
        val filename = normalized.substringAfterLast('/')
        val namedHistory = filename in setOf(
            "watch history json",
            "watch_history json",
            "watch history",
            "myactivity json",
            "my activity json",
        ) || filename.startsWith("myactivity ") || filename.startsWith("my activity ")
        return namedHistory || YoutubeTakeoutPathHints.isHistoryEntry(path) || "youtube" in normalized
    }

    fun read(input: InputStream, sink: PortabilityRecordSink) {
        PortabilityJsonFactory.createParser(input.buffered()).use { parser ->
            read(parser, sink)
        }
    }

    private fun read(parser: JsonParser, sink: PortabilityRecordSink) {
        if (parser.nextToken() != JsonToken.START_ARRAY) {
            parser.skipChildren()
            return
        }
        var count = 0
        var invalidDates = 0L
        var historyRecords = 0L
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            require(count++ < PortabilityLimits.MAX_CONTAINER_RECORDS) { "YouTube Takeout JSON contains too many records" }
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                parser.skipChildren()
                continue
            }
            val entry = YoutubeTakeoutJsonEntryReader.read(parser)
            if (entry.isAd()) continue
            if (!entry.isYoutubeActivity()) continue
            val directUrl = findVideoUrl(entry.titleUrl)
            val embeddedUrl = findVideoUrl(entry.title) ?: findVideoUrl(entry.description)
            val url = directUrl ?: embeddedUrl
            val timestamp = entry.time.takeIf(String::isNotBlank)?.let(YoutubeTakeoutDateParser::parseEpochMillis)
            val history = url != null && entry.shouldImportHistory(directUrl, embeddedUrl)
            val liked = YoutubeTakeoutActivityClassifier.isLikedAction(entry.title) ||
                entry.controls.any(YoutubeTakeoutActivityClassifier::isLiked)
            if (url != null && timestamp != null && history) {
                val video = entry.video(url)
                sink.write(PortabilityHistory(video, timestamp))
                historyRecords += 1
                if (liked) sink.write(PortabilityFavorite(video, timestamp))
            } else if (url != null && timestamp != null && liked) {
                sink.write(PortabilityFavorite(entry.video(url), timestamp))
            } else if (url != null && history) {
                invalidDates += 1
            }
            if (YoutubeTakeoutActivityClassifier.isSubscribedAction(entry.title) ||
                entry.controls.any(YoutubeTakeoutActivityClassifier::isSubscribed)
            ) {
                entry.channel()?.let { sink.write(PortabilitySubscription(it.url, it.name)) }
            }
        }
        if (historyRecords > 0) sink.markCategory(PortabilityCategory.HISTORY)
        if (invalidDates > 0) {
            sink.issue(
                PortabilityIssue(
                    PortabilityCategory.HISTORY,
                    "invalid_takeout_date",
                    "YouTube Takeout JSON rows with an invalid date were skipped",
                    invalidDates,
                ),
            )
        }
    }

    private fun YoutubeTakeoutJsonEntry.video(url: String): PortabilityVideo {
        val title = actionSuffixRegex.replace(actionPrefixRegex.replace(substringBeforeUrl(), "").trim(), "").trim().ifBlank {
            "YouTube video ${youtubeId(url)}"
        }
        val channel = channel()
        return PortabilityVideo(
            url = url,
            title = title,
            thumbnailUrl = "https://i.ytimg.com/vi/${youtubeId(url)}/hqdefault.jpg",
            channelName = channel?.name.orEmpty(),
            channelUrl = channel?.url.orEmpty(),
        )
    }

    private fun YoutubeTakeoutJsonEntry.channel(): YoutubeTakeoutJsonSubtitle? {
        subtitles.firstOrNull { channelUrlRegex.containsMatchIn(it.url) }?.let { return it }
        val url = channelUrlRegex.find(titleUrl)?.value ?: return null
        val name = actionSuffixRegex.replace(actionPrefixRegex.replace(title, "").trim(), "").trim()
        return YoutubeTakeoutJsonSubtitle(name, url)
    }

    private fun YoutubeTakeoutJsonEntry.substringBeforeUrl(): String = title.replace(videoUrlRegex, "").trim()

    private fun YoutubeTakeoutJsonEntry.isYoutubeActivity(): Boolean {
        val markers = buildList {
            if (header.isNotBlank()) add(header)
            addAll(products)
            addAll(controls)
        }
        return markers.isEmpty() || markers.any { YoutubeTakeoutTextNormalizer.normalize(it).contains("youtube") }
    }

    private fun YoutubeTakeoutJsonEntry.shouldImportHistory(directUrl: String?, embeddedUrl: String?): Boolean {
        if (isNavigationActivity()) return false
        if (YoutubeTakeoutActivityClassifier.isWatchedAction(title)) return true
        if (controls.any(YoutubeTakeoutPathHints::isHistoryEntry)) return true
        if (YoutubeTakeoutActivityClassifier.isLikedAction(title) ||
            YoutubeTakeoutActivityClassifier.isSubscribedAction(title) ||
            controls.any(YoutubeTakeoutActivityClassifier::isLiked) ||
            controls.any(YoutubeTakeoutActivityClassifier::isSubscribed)
        ) return false
        return directUrl != null && header.isBlank() && products.isEmpty() && controls.isEmpty() && embeddedUrl == null
    }

    private fun YoutubeTakeoutJsonEntry.isNavigationActivity(): Boolean {
        val normalized = YoutubeTakeoutTextNormalizer.normalize(title)
        return normalized.startsWith("visited ") || normalized.startsWith("searched ") ||
            normalized.startsWith("viewed a post") || normalized.startsWith("viewed ads")
    }

    private fun YoutubeTakeoutJsonEntry.isAd(): Boolean = details.any {
        val normalized = YoutubeTakeoutTextNormalizer.normalize(it)
        normalized in setOf("ads", "advertisement", "advertisements", "annonces", "werbung") ||
            normalized.contains("google ads")
    } || YoutubeTakeoutTextNormalizer.normalize(title).let {
        it.startsWith("viewed ads on youtube") || it.startsWith("watched ads on youtube")
    }

    private fun findVideoUrl(value: String): String? = videoUrlRegex.find(value)?.value?.let {
        when {
            it.startsWith("URLs://", ignoreCase = true) -> "https://${it.substringAfter("://")}"
            it.startsWith("http://", ignoreCase = true) -> "https://${it.substringAfter("://")}"
            else -> it
        }
    }

    private fun youtubeId(url: String): String = url.substringAfter("v=", "").substringBefore('&').ifBlank {
        url.substringAfterLast('/').substringBefore('?')
    }

}
