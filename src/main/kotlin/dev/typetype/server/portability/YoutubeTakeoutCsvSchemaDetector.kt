package dev.typetype.server.portability

import dev.typetype.server.services.YoutubeTakeoutCsvReader
import dev.typetype.server.services.YoutubeTakeoutDateParser
import dev.typetype.server.services.YoutubeTakeoutSchemaHints
import dev.typetype.server.services.YoutubeTakeoutTextNormalizer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal enum class YoutubeTakeoutCsvKind {
    PLAYLIST_MANIFEST,
    SUBSCRIPTIONS,
    PLAYLIST_CONTENT,
    OTHER,
}

internal data class YoutubeTakeoutCsvClassification(
    val entry: ZipEntry,
    val kind: YoutubeTakeoutCsvKind,
    val maybePortable: Boolean,
)

internal object YoutubeTakeoutCsvSchemaDetector {
    fun classify(zip: ZipFile, entries: List<ZipEntry>): List<YoutubeTakeoutCsvClassification> = entries.asSequence()
        .filter { it.name.endsWith(".csv", ignoreCase = true) && "youtube" in it.name.lowercase() }
        .map { entry -> classify(zip, entry) }
        .toList()

    private fun classify(zip: ZipFile, entry: ZipEntry): YoutubeTakeoutCsvClassification {
        val stem = fileStem(entry)
        val path = YoutubeTakeoutSchemaHints.normalize(entry.name)
        if (YoutubeTakeoutSchemaHints.isPlaylistManifestName(stem)) {
            return YoutubeTakeoutCsvClassification(entry, YoutubeTakeoutCsvKind.PLAYLIST_MANIFEST, maybePortable = false)
        }
        if (YoutubeTakeoutSchemaHints.isSubscriptionText(stem)) {
            return YoutubeTakeoutCsvClassification(entry, YoutubeTakeoutCsvKind.SUBSCRIPTIONS, maybePortable = false)
        }
        if (YoutubeTakeoutSchemaHints.isPlaylistText(path)) {
            val shape = inspect(zip, entry)
            val kind = if (shape.isManifest()) YoutubeTakeoutCsvKind.PLAYLIST_MANIFEST else YoutubeTakeoutCsvKind.PLAYLIST_CONTENT
            return YoutubeTakeoutCsvClassification(entry, kind, maybePortable = false)
        }
        val shape = inspect(zip, entry)
        val kind = when {
            shape.isManifest() -> YoutubeTakeoutCsvKind.PLAYLIST_MANIFEST
            shape.isSubscription() -> YoutubeTakeoutCsvKind.SUBSCRIPTIONS
            shape.isContent() -> YoutubeTakeoutCsvKind.PLAYLIST_CONTENT
            else -> YoutubeTakeoutCsvKind.OTHER
        }
        return YoutubeTakeoutCsvClassification(entry, kind, shape.maybePortable())
    }

    private fun inspect(zip: ZipFile, entry: ZipEntry): CsvShape {
        var header = emptyList<String>()
        val rows = ArrayList<List<String>>(SAMPLE_ROWS)
        zip.getInputStream(entry).use { input ->
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
            YoutubeTakeoutCsvReader.forEach(reader, { header = it }) { row ->
                if (rows.size < SAMPLE_ROWS) rows += row
            }
        }
        return CsvShape(header, rows)
    }

    private fun fileStem(entry: ZipEntry): String = YoutubeTakeoutSchemaHints.normalize(
        entry.name.substringAfterLast('/').substringBeforeLast('.'),
    )

    private data class CsvShape(
        val header: List<String>,
        val rows: List<List<String>>,
    ) {
        private val hasChannelId by lazy {
            header.any(YoutubeTakeoutSchemaHints::isChannelIdHeader) || rows.hasValue(YoutubeTakeoutSchemaHints::looksLikeChannelId)
        }
        private val hasChannelUrl by lazy {
            header.any(YoutubeTakeoutSchemaHints::isChannelUrlHeader) || rows.hasValue(YoutubeTakeoutSchemaHints::containsChannelUrl)
        }
        private val hasVideoId by lazy {
            header.any(YoutubeTakeoutSchemaHints::isVideoIdHeader) || rows.hasValue(::isLikelyVideoId)
        }
        private val hasPlaylistId by lazy {
            header.any(YoutubeTakeoutSchemaHints::isPlaylistIdHeader) || rows.hasValue(YoutubeTakeoutSchemaHints::looksLikePlaylistId)
        }
        private val hasPlaylistKey by lazy {
            header.any(YoutubeTakeoutSchemaHints::isPlaylistIdHeader) ||
                header.any(YoutubeTakeoutSchemaHints::isPlaylistTitleHeader)
        }
        private val hasAddedAt by lazy {
            header.any(YoutubeTakeoutSchemaHints::isPlaylistItemAddedAtHeader) || rows.hasValue {
                YoutubeTakeoutDateParser.parseEpochMillis(it) != null
            }
        }

        fun isSubscription(): Boolean = hasChannelId && hasChannelUrl && !hasVideoId && !hasPlaylistId

        fun isManifest(): Boolean = hasPlaylistId && !hasVideoId &&
            (header.any(YoutubeTakeoutSchemaHints::isPlaylistTitleHeader) || rows.any { row -> row.any(::isTextValue) })

        fun isContent(): Boolean {
            if (!hasVideoId || hasChannelId) return false
            return hasPlaylistKey || header.size <= 3 && (hasAddedAt || rows.isNotEmpty())
        }

        fun maybePortable(): Boolean = isSubscription() || isManifest() || isContent() ||
            hasChannelUrl || hasPlaylistId || hasVideoId && !hasChannelId && header.size <= 3

        private fun isTextValue(value: String): Boolean {
            val trimmed = value.trim()
            if (trimmed.isBlank() || YoutubeTakeoutSchemaHints.looksLikeChannelId(trimmed) ||
                YoutubeTakeoutSchemaHints.looksLikePlaylistId(trimmed) ||
                isLikelyVideoId(trimmed) ||
                YoutubeTakeoutSchemaHints.containsChannelUrl(trimmed) ||
                YoutubeTakeoutSchemaHints.containsWatchUrl(trimmed) ||
                YoutubeTakeoutDateParser.parseEpochMillis(trimmed) != null
            ) return false
            return YoutubeTakeoutTextNormalizer.normalize(trimmed) !in NON_TEXT_VALUES
        }

        private fun isLikelyVideoId(value: String): Boolean =
            YoutubeTakeoutSchemaHints.looksLikeLikelyVideoId(value)

        private fun List<List<String>>.hasValue(predicate: (String) -> Boolean): Boolean = any { row -> row.any(predicate) }
    }

    private val NON_TEXT_VALUES = setOf(
        "true", "false", "yes", "no", "oui", "non", "faux", "vrai", "public", "private", "prive",
        "publique", "privee", "unlisted", "non repertorie",
    )
    private const val SAMPLE_ROWS = 64
}
