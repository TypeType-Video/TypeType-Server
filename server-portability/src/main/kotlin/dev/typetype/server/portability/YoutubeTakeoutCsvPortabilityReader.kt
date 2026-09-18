package dev.typetype.server.portability

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.services.YoutubeTakeoutCsvReader
import dev.typetype.server.services.YoutubeTakeoutRowParser
import dev.typetype.server.services.YoutubeTakeoutSchemaHints
import dev.typetype.server.services.YoutubeTakeoutSystemPlaylist
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object YoutubeTakeoutCsvPortabilityReader {
    private const val PLAYLIST_LOOKUP = "youtube-takeout-playlist"

    fun read(zip: ZipFile, entries: List<ZipEntry>, sink: PortabilityRecordSink) {
        val classified = YoutubeTakeoutCsvSchemaDetector.classify(zip, entries)
        classified.filter { it.kind == YoutubeTakeoutCsvKind.PLAYLIST_MANIFEST }.forEach { classifiedEntry ->
            sink.markCategory(PortabilityCategory.PLAYLISTS)
            forEachRow(zip, classifiedEntry.entry) { header, row ->
                val playlist = YoutubeTakeoutRowParser.parsePlaylist(header, row) ?: return@forEachRow
                indexPlaylist(playlist, sink)
                if (!isSystemPlaylist(playlist)) {
                    sink.write(PortabilityPlaylist(playlist.id, playlist.name, playlist.description, playlist.createdAt))
                }
            }
        }
        classified.filter { it.kind == YoutubeTakeoutCsvKind.SUBSCRIPTIONS }
            .forEach { readSubscriptions(zip, it.entry, sink) }
        classified.filter { it.kind == YoutubeTakeoutCsvKind.PLAYLIST_CONTENT }
            .forEach { readPlaylistItems(zip, it.entry, sink) }
        classified.filter { it.kind == YoutubeTakeoutCsvKind.OTHER && it.maybePortable }
            .forEach { classifiedEntry ->
                sink.issue(
                    PortabilityIssue(
                        category = null,
                        code = "unsupported_takeout_csv",
                        message = "A YouTube Takeout CSV file could not be classified and was skipped: ${classifiedEntry.entry.name}",
                    ),
                )
            }
    }

    private fun readSubscriptions(zip: ZipFile, entry: ZipEntry, sink: PortabilityRecordSink) {
        sink.markCategory(PortabilityCategory.SUBSCRIPTIONS)
        forEachRow(zip, entry) { header, row ->
            val item = YoutubeTakeoutRowParser.parseSubscription(header, row)
            if (item == null) sink.invalid(PortabilityCategory.SUBSCRIPTIONS, "subscription")
            else sink.write(item.toPortability())
        }
    }

    private fun readPlaylistItems(zip: ZipFile, entry: ZipEntry, sink: PortabilityRecordSink) {
        var inferredKey = playlistKeyFromPath(entry.name)
        var rowPosition = 0
        forEachRow(zip, entry) { originalHeader, row ->
            val hasKey = originalHeader.any(YoutubeTakeoutSchemaHints::isPlaylistIdHeader) ||
                originalHeader.any(YoutubeTakeoutSchemaHints::isPlaylistTitleHeader)
            val header = if (hasKey || inferredKey == null) originalHeader else listOf("playlist source key") + originalHeader
            val values = if (header === originalHeader) row else listOf(requireNotNull(inferredKey)) + row
            if (YoutubeTakeoutRowParser.isUnavailablePlaylistItem(header, values)) return@forEachRow
            val parsed = YoutubeTakeoutRowParser.parsePlaylistItem(header, values)
            if (parsed == null) {
                sink.invalid(PortabilityCategory.PLAYLISTS, "playlist item")
                return@forEachRow
            }
            inferredKey = parsed.first
            val item = parsed.second.copy(position = parsed.second.position.takeIf { it > 0 } ?: rowPosition)
            writePlaylistVideo(parsed.first, item, sink)
            rowPosition += 1
        }
    }

    private fun writePlaylistVideo(key: String, item: dev.typetype.server.models.PlaylistVideoItem, sink: PortabilityRecordSink) {
        when (val resolved = YoutubeTakeoutSystemPlaylist.canonicalKey(key) ?: sink.lookup(PLAYLIST_LOOKUP, key)) {
            YoutubeTakeoutSystemPlaylist.WATCH_LATER -> sink.write(
                PortabilityWatchLater(item.toPortabilityVideo(), item.addedAt),
            )
            YoutubeTakeoutSystemPlaylist.LIKED_VIDEOS -> sink.write(
                PortabilityFavorite(item.toPortabilityVideo(), item.addedAt),
            )
            null -> {
                val sourceId = key.trim()
                sink.write(PortabilityPlaylist(sourceId, sourceId))
                sink.write(PortabilityPlaylistVideo(sourceId, item.position, item.toPortabilityVideo(), item.addedAt))
                sink.issue(PortabilityIssue(PortabilityCategory.PLAYLISTS, "playlist_manifest_missing", "A playlist was reconstructed from its item file"))
            }
            else -> sink.write(PortabilityPlaylistVideo(resolved, item.position, item.toPortabilityVideo(), item.addedAt))
        }
    }

    private fun indexPlaylist(item: PlaylistItem, sink: PortabilityRecordSink) {
        val resolved = YoutubeTakeoutSystemPlaylist.canonicalKey(item.id) ?: YoutubeTakeoutSystemPlaylist.canonicalKey(item.name)
            ?: item.id.ifBlank { item.name }
        if (item.id.isNotBlank()) sink.putLookup(PLAYLIST_LOOKUP, item.id, resolved)
        playlistAliases(item.name).forEach { sink.putLookup(PLAYLIST_LOOKUP, it, resolved) }
    }

    private fun forEachRow(zip: ZipFile, entry: ZipEntry, block: (List<String>, List<String>) -> Unit) {
        zip.getInputStream(entry).use { input ->
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
            var header = emptyList<String>()
            YoutubeTakeoutCsvReader.forEach(reader, { header = it }) { row -> block(header, row) }
        }
    }

    private fun playlistKeyFromPath(path: String): String? = path.substringAfterLast('/').substringBeforeLast('.')
        .takeUnless { YoutubeTakeoutSchemaHints.normalize(it) == "playlist items" }
        ?.let { YoutubeTakeoutSystemPlaylist.canonicalKey(it) ?: it }

    private fun isSystemPlaylist(item: PlaylistItem) =
        YoutubeTakeoutSystemPlaylist.canonicalKey(item.id) != null || YoutubeTakeoutSystemPlaylist.canonicalKey(item.name) != null

    private fun playlistAliases(name: String): Set<String> = setOf(
        name,
    ) + filenameAliases(name) + localizedPlaylistAliases(name)

    private fun filenameAliases(name: String): Set<String> {
        val escaped = name.replace('/', '_').replace('\'', '_')
        return setOf(
            "$escaped-videos",
            "$escaped-vídeos",
        )
    }

    private fun localizedPlaylistAliases(name: String): Set<String> = setOf(
        "Videos from $name",
        "Videos de $name",
        "Vídeos de $name",
        "Vidéos de $name",
        "Videos da playlist $name",
        "Vídeos da playlist $name",
        "Video di $name",
        "Video da $name",
        "Videos von $name",
        "Videos van $name",
        "Filmy z playlisty $name",
        "Видео из $name",
        "動画 - $name",
        "$name の動画",
        "$name 동영상",
        "$name 视频",
        "$name 影片",
        "$name videos",
    )

    private fun PortabilityRecordSink.invalid(category: PortabilityCategory, kind: String) =
        issue(PortabilityIssue(category, "invalid_takeout_row", "An invalid YouTube Takeout $kind row was skipped"))

}
