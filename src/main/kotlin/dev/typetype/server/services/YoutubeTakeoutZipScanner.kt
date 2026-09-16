package dev.typetype.server.services

import java.io.InputStreamReader
import java.io.BufferedReader
import java.nio.file.Path
import java.util.zip.ZipFile

object YoutubeTakeoutZipScanner {
    fun scan(zipPath: Path): YoutubeTakeoutZipScanResult {
        val warnings = mutableListOf<String>()
        var subscriptionsHeader = emptyList<String>()
        val subscriptionsRows = mutableListOf<List<String>>()
        var playlistsHeader = emptyList<String>()
        val playlistsRows = mutableListOf<List<String>>()
        var playlistItemsHeader = emptyList<String>()
        val playlistItemsRows = mutableListOf<List<String>>()
        ZipFile(zipPath.toFile()).use { zip ->
            if (zip.size() > YoutubeTakeoutLimits.MAX_ZIP_ENTRIES) error("Archive contains too many files")
            zip.entries().asSequence().filter { it.isDirectory.not() }.forEach { entry ->
                if (entry.size > YoutubeTakeoutLimits.MAX_TMP_BYTES) error("Archive entry too large")
                val name = entry.name.lowercase()
                if (!name.endsWith(".csv")) return@forEach
                zip.getInputStream(entry).use { input ->
                    val reader = BufferedReader(InputStreamReader(input))
                    val (header, rows) = YoutubeTakeoutCsvReader.parse(reader)
                    if (header.isEmpty()) return@use
                    when {
                        isSubscriptionsEntry(entry.name, header, rows) -> {
                            if (subscriptionsHeader.isEmpty()) subscriptionsHeader = header
                            subscriptionsRows += rows
                        }
                        isPlaylistsHeader(entry.name, header, rows) -> {
                            if (playlistsHeader.isEmpty()) playlistsHeader = header
                            playlistsRows += rows
                        }
                        isPlaylistItemsEntry(name, header, rows) -> {
                            val sourceKey = extractPlaylistSourceKey(entry.name, header)
                            if (sourceKey == null) {
                                if (playlistItemsHeader.isEmpty()) playlistItemsHeader = header
                                playlistItemsRows += rows
                            } else {
                                if (playlistItemsHeader.isEmpty()) playlistItemsHeader = listOf("playlist source key") + header
                                playlistItemsRows += rows.map { row -> listOf(sourceKey) + row }
                            }
                        }
                        else -> warnings += "Unsupported CSV schema: ${entry.name}"
                    }
                }
            }
        }
        return YoutubeTakeoutZipScanResult(
            subscriptionsRows,
            subscriptionsHeader,
            playlistsRows,
            playlistsHeader,
            playlistItemsRows,
            playlistItemsHeader,
            warnings,
        )
    }

    private fun isSubscriptionsEntry(path: String, header: List<String>, rows: List<List<String>>): Boolean {
        val hasId = header.any(YoutubeTakeoutSchemaHints::isChannelIdHeader) || rows.hasValue(YoutubeTakeoutSchemaHints::looksLikeChannelId)
        val hasUrl = header.any(YoutubeTakeoutSchemaHints::isChannelUrlHeader) || rows.hasValue(YoutubeTakeoutSchemaHints::containsChannelUrl)
        val hasTitle = header.any(YoutubeTakeoutSchemaHints::isChannelTitleHeader)
        return hasId && (hasUrl || (hasTitle && isSubscriptionPath(path)))
    }

    private fun isPlaylistsHeader(path: String, header: List<String>, rows: List<List<String>>): Boolean {
        val hasId = header.any(YoutubeTakeoutSchemaHints::isPlaylistIdHeader) || rows.hasValue(YoutubeTakeoutSchemaHints::looksLikePlaylistId)
        val hasTitle = header.any(YoutubeTakeoutSchemaHints::isPlaylistTitleHeader)
        return hasId && hasTitle && (isMainPlaylistsFile(path) || isPlaylistPath(path))
    }

    private fun isPlaylistItemsEntry(path: String, header: List<String>, rows: List<List<String>>): Boolean {
        if (isMainPlaylistsFile(path)) return false
        val hasVideo = header.any(YoutubeTakeoutSchemaHints::isVideoIdHeader) || rows.hasValue(YoutubeTakeoutSchemaHints::looksLikeVideoId)
        val hasPlaylistKey = header.any(YoutubeTakeoutSchemaHints::isPlaylistIdHeader) ||
            header.any(YoutubeTakeoutSchemaHints::isPlaylistTitleHeader)
        return hasVideo && (isPlaylistPath(path) || isPlaylistItemsFile(path) || hasPlaylistKey)
    }

    private fun extractPlaylistSourceKey(path: String, header: List<String>): String? {
        if (header.any(YoutubeTakeoutSchemaHints::isPlaylistIdHeader) || header.any(YoutubeTakeoutSchemaHints::isPlaylistTitleHeader)) return null
        val fileName = path.substringAfterLast('/').substringBeforeLast('.')
        return YoutubeTakeoutSystemPlaylist.canonicalKey(fileName) ?: fileName
    }

    private fun isSubscriptionPath(path: String): Boolean {
        return YoutubeTakeoutSchemaHints.isSubscriptionText(path)
    }

    private fun isPlaylistPath(path: String): Boolean = YoutubeTakeoutSchemaHints.isPlaylistText(path)

    private fun isPlaylistItemsFile(path: String): Boolean =
        path.substringAfterLast('/').substringBeforeLast('.').let { YoutubeTakeoutSchemaHints.normalize(it) == "playlist items" }

    private fun isMainPlaylistsFile(path: String): Boolean =
        path.substringAfterLast('/').substringBeforeLast('.').let { fileName ->
            YoutubeTakeoutSchemaHints.isPlaylistManifestName(fileName)
        }

    private fun List<List<String>>.hasValue(predicate: (String) -> Boolean): Boolean = any { row -> row.any(predicate) }

}
