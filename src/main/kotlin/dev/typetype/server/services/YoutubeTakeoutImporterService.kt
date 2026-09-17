package dev.typetype.server.services

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.YoutubeTakeoutCategoryCounts
import dev.typetype.server.models.YoutubeTakeoutCommitPlan
import dev.typetype.server.models.YoutubeTakeoutImportReportItem
import dev.typetype.server.models.YoutubeTakeoutImportStats
import dev.typetype.server.models.YoutubeTakeoutParsedData
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.atomic.AtomicLong

class YoutubeTakeoutImporterService(
    private val subscriptionsService: SubscriptionsService,
    private val playlistService: PlaylistService,
    private val signalImportService: YoutubeTakeoutSignalImportService,
    private val playlistKeyService: YoutubeTakeoutPlaylistKeyService = YoutubeTakeoutPlaylistKeyService(),
) {
    suspend fun commit(
        userId: String,
        parsed: YoutubeTakeoutParsedData,
        plan: YoutubeTakeoutCommitPlan,
        onProgress: (processed: Long, total: Long) -> Unit = { _, _ -> },
    ): YoutubeTakeoutImportReportItem = coroutineScope {
        val (issues, issueSummary) = YoutubeTakeoutIssueService.build(parsed.warnings, parsed.errors, stage = "commit")
        val total = importTotal(parsed, plan)
        val processed = AtomicLong()
        val progress = { onProgress(processed.incrementAndGet(), total) }
        val existingSubsDeferred = async { subscriptionsService.getAll(userId).map { it.channelUrl }.toSet() }
        val existingPlaylistsDeferred = async { playlistService.getAll(userId) }
        val sourceMappingsDeferred = async { playlistKeyService.getMappings(userId).toMutableMap() }
        val existingSubs = existingSubsDeferred.await().toMutableSet()
        val existingPlaylistRows = existingPlaylistsDeferred.await()
        val existingPlaylists = existingPlaylistRows.associateBy { it.name.lowercase() }
        val sourceMappings = sourceMappingsDeferred.await()
        var subImported = 0
        var subSkipped = 0
        if (plan.importSubscriptions) {
            parsed.subscriptions.forEach { item ->
                val canonicalUrl = ChannelUrlCanonicalizer.canonicalize(item.channelUrl)
                if (canonicalUrl in existingSubs) subSkipped += 1 else {
                    subscriptionsService.add(userId, SubscriptionItem(item.channelUrl, item.name, item.avatarUrl))
                    existingSubs += canonicalUrl
                    subImported += 1
                }
                progress()
            }
        }
        var plImported = 0
        var plSkipped = 0
        var itemImported = 0
        var itemSkipped = 0
        val createdBySource = mutableMapOf<String, PlaylistItem>()
        val favorites = parsed.favorites.map { it.withYoutubeFallbackTitle() }
        if (plan.importPlaylists) {
            parsed.playlists.forEach { item ->
                if (YoutubeTakeoutSystemPlaylist.canonicalKey(item.name) != null || YoutubeTakeoutSystemPlaylist.canonicalKey(item.id) != null) {
                    plSkipped += 1
                    progress()
                    return@forEach
                }
                val nameKey = item.name.lowercase()
                val idKey = item.id.lowercase()
                val mappedPlaylistId = sourceMappings[idKey].orEmpty()
                val mapped = if (mappedPlaylistId.isBlank()) null else playlistService.getById(userId, mappedPlaylistId)
                val existing = mapped ?: existingPlaylists[nameKey]
                val playlist = if (existing != null) {
                    plSkipped += 1
                    existing
                } else {
                    val created = playlistService.create(userId, PlaylistItem(name = item.name, description = item.description))
                    plImported += 1
                    created
                }
                createdBySource[nameKey] = playlist
                if (idKey.isNotBlank()) {
                    createdBySource[idKey] = playlist
                    playlistKeyService.putMapping(userId, idKey, playlist.id)
                    sourceMappings[idKey] = playlist.id
                }
                progress()
            }
        }
        if (plan.importPlaylistItems) {
            parsed.playlistItems.forEach { (playlistKey, videos) ->
                val normalizedKey = playlistKey.lowercase()
                if (YoutubeTakeoutSystemPlaylist.canonicalKey(normalizedKey) != null) {
                    itemSkipped += videos.size
                    repeat(videos.size) { progress() }
                    return@forEach
                }
                val mappedId = sourceMappings[normalizedKey].orEmpty()
                val mappedPlaylist = if (mappedId.isBlank()) null else playlistService.getById(userId, mappedId)
                val playlist = mappedPlaylist ?: createdBySource[normalizedKey]
                if (playlist == null) {
                    itemSkipped += videos.size
                    repeat(videos.size) { progress() }
                    return@forEach
                }
                val existingUrls = playlistService.getById(userId, playlist.id)?.videos?.map { it.url }.orEmpty().toMutableSet()
                videos.forEach { video ->
                    if (video.url in existingUrls) itemSkipped += 1 else {
                        playlistService.addVideo(userId, playlist.id, video)
                        itemImported += 1
                        existingUrls += video.url
                    }
                    progress()
                }
            }
        }
        val favoriteDeferred = if (plan.importFavorites) async {
            signalImportService.importFavorites(userId, favorites) { progress() }
        } else null
        val watchLaterDeferred = if (plan.importWatchLater) async {
            signalImportService.importWatchLater(userId, parsed.watchLater) { progress() }
        } else null
        val historyDeferred = if (plan.importHistory) async {
            signalImportService.importHistory(userId, parsed.history) { progress() }
        } else null
        val emptyStats = YoutubeTakeoutImportStats(0, 0, 0)
        val favoriteStats = favoriteDeferred?.await() ?: emptyStats
        val watchLaterStats = watchLaterDeferred?.await() ?: emptyStats
        val historyStats = historyDeferred?.await() ?: emptyStats
        val report = YoutubeTakeoutImportReportItem(
            subscriptions = YoutubeTakeoutImportStats(imported = subImported, skipped = subSkipped, failed = 0),
            playlists = YoutubeTakeoutImportStats(imported = plImported, skipped = plSkipped, failed = 0),
            playlistItems = YoutubeTakeoutImportStats(imported = itemImported, skipped = itemSkipped, failed = 0),
            favorites = favoriteStats,
            watchLater = watchLaterStats,
            history = historyStats,
            skippedItems = YoutubeTakeoutCategoryCounts(subSkipped, plSkipped, itemSkipped, favoriteStats.skipped, watchLaterStats.skipped, historyStats.skipped),
            warnings = parsed.warnings,
            errors = parsed.errors,
            issues = issues,
            issueSummary = issueSummary,
            finishedAt = System.currentTimeMillis(),
        )
        if (plan.importSubscriptions) SubscriptionFeedCacheInvalidation.invalidate(userId)
        report
    }

    private fun importTotal(parsed: YoutubeTakeoutParsedData, plan: YoutubeTakeoutCommitPlan): Long = buildList {
        if (plan.importSubscriptions) add(parsed.subscriptions.size.toLong())
        if (plan.importPlaylists) add(parsed.playlists.size.toLong())
        if (plan.importPlaylistItems) add(parsed.playlistItems.values.sumOf { it.size }.toLong())
        if (plan.importFavorites) add(parsed.favorites.size.toLong())
        if (plan.importWatchLater) add(parsed.watchLater.size.toLong())
        if (plan.importHistory) add(parsed.history.size.toLong())
    }.sum()
}
