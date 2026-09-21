package dev.typetype.server.services

import dev.typetype.server.models.HistoryItem
import dev.typetype.server.models.FavoriteItem
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.YoutubeTakeoutParsedData
import java.nio.file.Path
import java.util.zip.ZipFile

class YoutubeTakeoutParserService {
    fun parse(zipPath: Path): YoutubeTakeoutParsedData {
        val scan = YoutubeTakeoutZipScanner.scan(zipPath)
        val warnings = scan.warnings.toMutableList()
        val errors = mutableListOf<String>()
        val subscriptions = scan.subscriptionsRows.mapNotNull { row ->
            runCatching { YoutubeTakeoutRowParser.parseSubscription(scan.subscriptionsHeader, row) }.getOrElse {
                errors += "Invalid subscription row"
                null
            }
        }
        val playlists = scan.playlistsRows.mapNotNull { row ->
            runCatching { YoutubeTakeoutRowParser.parsePlaylist(scan.playlistsHeader, row) }.getOrElse {
                errors += "Invalid playlist row"
                null
            }
        }
        val history = parseHistory(zipPath, warnings)
        val historyByVideoId = history.mapNotNull { item ->
            YoutubeTypeTypeMapper.videoId(item.url)?.let { it to item }
        }.toMap()
        val rawPlaylistItems = mutableMapOf<String, MutableList<PlaylistVideoItem>>()
        scan.playlistItemsRows.forEach { row ->
            if (YoutubeTakeoutRowParser.isUnavailablePlaylistItem(scan.playlistItemsHeader, row)) return@forEach
            val parsed = runCatching { YoutubeTakeoutRowParser.parsePlaylistItem(scan.playlistItemsHeader, row) }.getOrNull()
            if (parsed == null) {
                errors += "Invalid playlist item row"
            } else {
                rawPlaylistItems.getOrPut(parsed.first) { mutableListOf() }
                    .add(YoutubeTypeTypeMapper.playlistVideo(parsed.second, historyByVideoId))
            }
        }
        val dedupedPlaylists = dedupPlaylists(playlists)
        val playlistItems = YoutubeTakeoutPlaylistKeyResolver.resolveAll(rawPlaylistItems, dedupedPlaylists)
        val watchLater = playlistItems.filterKeys { isWatchLaterPlaylistKey(it) }.values.flatten()
        val favorites = playlistItems.filterKeys { isLikedPlaylistKey(it) }.values.flatten().map { it.toFavorite() }
        val activitySignals = YoutubeTakeoutActivitySignalService.parse(zipPath)
        val mergedSubscriptions = dedupSubscriptions(subscriptions + activitySignals.first)
        val mergedFavorites = dedupFavorites(favorites + activitySignals.second.map { it.withYoutubeFallbackTitle() })
        if (mergedSubscriptions.isEmpty()) warnings += "No subscription rows detected"
        return YoutubeTakeoutParsedData(
            subscriptions = mergedSubscriptions,
            playlists = dedupedPlaylists.filterNot(::isSystemPlaylist),
            playlistItems = dedupPlaylistItems(playlistItems),
            favorites = mergedFavorites,
            watchLater = watchLater.distinctBy { it.url },
            history = dedupHistory(history),
            warnings = warnings,
            errors = errors,
        )
    }

    private fun dedupSubscriptions(items: List<SubscriptionItem>): List<SubscriptionItem> = items.distinctBy { it.channelUrl }

    private fun dedupPlaylists(items: List<PlaylistItem>): List<PlaylistItem> = items.distinctBy { it.id.ifBlank { it.name } }

    private fun dedupPlaylistItems(map: Map<String, List<PlaylistVideoItem>>): Map<String, List<PlaylistVideoItem>> =
        map.mapValues { (_, items) -> items.distinctBy { it.url } }

    private fun dedupFavorites(items: List<FavoriteItem>): List<FavoriteItem> = items.distinctBy { it.videoUrl }

    private fun dedupHistory(items: List<HistoryItem>): List<HistoryItem> = items.distinctBy { it.url to it.watchedAt }

    private fun PlaylistVideoItem.toFavorite(): FavoriteItem = FavoriteItem(
        videoUrl = url,
        favoritedAt = addedAt,
        title = title,
        thumbnail = thumbnail,
        duration = duration,
        channelName = channelName,
        channelUrl = channelUrl,
        channelAvatar = channelAvatar,
        viewCount = viewCount,
        publishedAt = publishedAt,
    )

    private fun isSystemPlaylist(item: PlaylistItem): Boolean =
        isLikedPlaylistKey(item.name) || isWatchLaterPlaylistKey(item.name) ||
            isLikedPlaylistKey(item.id) || isWatchLaterPlaylistKey(item.id)

    private fun parseHistory(zipPath: Path, warnings: MutableList<String>): List<HistoryItem> {
        ZipFile(zipPath.toFile()).use { zip ->
            val entries = zip.entries().asSequence().filter { item ->
                !item.isDirectory && YoutubeTakeoutPathHints.isYoutubeHtml(item.name)
            }.toList()
            val entry = entries.firstOrNull { YoutubeTakeoutPathHints.isHistoryEntry(it.name) }
                ?: entries.firstOrNull { YoutubeTakeoutTextNormalizer.normalize(it.name).contains("monactiv") }
                ?: entries.firstOrNull()
            if (entry == null) return emptyList()
            val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val parsed = YoutubeTakeoutHistoryParser.parseWithDiagnostics(
                html,
                requireWatchedMarker = !YoutubeTakeoutPathHints.isHistoryEntry(entry.name),
            )
            if (parsed.items.isEmpty()) warnings += "No watch history rows detected"
            if (parsed.invalidDates > 0) warnings += "Skipped ${parsed.invalidDates} watch history rows with invalid dates"
            return parsed.items.map(YoutubeTypeTypeMapper::historyItem)
        }
    }

    private fun isLikedPlaylistKey(value: String): Boolean = YoutubeTakeoutSystemPlaylist.isLiked(value)

    private fun isWatchLaterPlaylistKey(value: String): Boolean = YoutubeTakeoutSystemPlaylist.isWatchLater(value)
}
