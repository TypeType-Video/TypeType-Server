package dev.typetype.server.services

import dev.typetype.server.models.HistoryItem
import dev.typetype.server.models.FavoriteItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.WatchLaterItem
import dev.typetype.server.models.YoutubeTakeoutImportStats

class YoutubeTakeoutSignalImportService(
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
    private val historyService: HistoryService,
) {
    suspend fun importFavorites(
        userId: String,
        items: List<FavoriteItem>,
        onProgress: () -> Unit = {},
    ): YoutubeTakeoutImportStats {
        var imported = 0
        var skipped = 0
        val existing = favoritesService.getAll(userId).map { it.videoUrl }.toMutableSet()
        items.forEach { item ->
            if (item.videoUrl in existing) skipped += 1 else {
                favoritesService.add(userId, item)
                imported += 1
                existing += item.videoUrl
            }
            onProgress()
        }
        return YoutubeTakeoutImportStats(imported = imported, skipped = skipped, failed = 0)
    }

    suspend fun importWatchLater(
        userId: String,
        videos: List<PlaylistVideoItem>,
        onProgress: () -> Unit = {},
    ): YoutubeTakeoutImportStats {
        var imported = 0
        var skipped = 0
        val existing = watchLaterService.getAll(userId).map { it.url }.toMutableSet()
        videos.forEach { video ->
            if (video.url in existing) skipped += 1 else {
                watchLaterService.add(userId, video.toWatchLaterItem())
                imported += 1
                existing += video.url
            }
            onProgress()
        }
        return YoutubeTakeoutImportStats(imported = imported, skipped = skipped, failed = 0)
    }

    suspend fun importHistory(
        userId: String,
        items: List<HistoryItem>,
        onProgress: () -> Unit = {},
    ): YoutubeTakeoutImportStats {
        var skipped = 0
        val existing = historyService.dedupKeys(userId).toMutableSet()
        val toInsert = mutableListOf<HistoryItem>()
        items.forEach { item ->
            val key = item.url to item.watchedAt
            if (key in existing) skipped += 1 else {
                toInsert += item
                existing += key
            }
            onProgress()
        }
        val imported = historyService.addImportedBatch(userId, toInsert)
        return YoutubeTakeoutImportStats(imported = imported, skipped = skipped, failed = 0)
    }

    private fun PlaylistVideoItem.toWatchLaterItem(): WatchLaterItem = WatchLaterItem(
        url = url,
        title = title,
        thumbnail = thumbnail,
        duration = duration,
        addedAt = addedAt,
        channelName = channelName,
        channelUrl = channelUrl,
        channelAvatar = channelAvatar,
        viewCount = viewCount,
        publishedAt = publishedAt,
    )
}
