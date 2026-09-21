package dev.typetype.server.services

import dev.typetype.server.models.TYPE_TYPE_BACKUP_FORMAT
import dev.typetype.server.models.TYPE_TYPE_BACKUP_VERSION
import dev.typetype.server.models.TypeTypeBackupItem
import dev.typetype.server.models.TypeTypeContentFiltersBackup
import dev.typetype.server.models.TypeTypeRestoreSummary
import java.util.Locale

class TypeTypeBackupService(
    private val subscriptions: SubscriptionsService,
    private val history: HistoryService,
    private val playlists: PlaylistService,
    private val watchLater: WatchLaterService,
    private val favorites: FavoritesService,
    private val progress: ProgressService,
    private val searchHistory: SearchHistoryService,
    private val savedPlaylists: SavedPlaylistService,
    private val settings: SettingsService,
    private val blocked: BlockedService,
    private val allowedChannels: AllowedChannelsService,
    private val allowedPlaylists: AllowedPlaylistsService,
) {
    suspend fun export(
        userId: String,
        categories: Set<TypeTypeBackupCategory>,
    ): TypeTypeBackupItem {
        fun includes(category: TypeTypeBackupCategory) = category in categories
        val fullPlaylists = if (includes(TypeTypeBackupCategory.PLAYLISTS)) {
            playlists.getAll(userId).mapNotNull { playlists.getById(userId, it.id) }
        } else {
            null
        }
        val subscriptionItems = if (includes(TypeTypeBackupCategory.SUBSCRIPTIONS)) {
            subscriptions.getAll(userId)
        } else {
            null
        }
        return TypeTypeBackupItem(
            exportedAt = System.currentTimeMillis(),
            categories = categories.map(TypeTypeBackupCategory::wireName).sorted(),
            subscriptions = subscriptionItems,
            subscriptionGroups = subscriptionItems?.let { items ->
                val channelUrls = items.mapTo(hashSetOf()) {
                    ChannelUrlCanonicalizer.canonicalize(it.channelUrl)
                }
                SubscriptionGroupBackupRepository.export(userId, channelUrls)
            },
            history = if (includes(TypeTypeBackupCategory.HISTORY)) history.getAll(userId) else null,
            playlists = fullPlaylists,
            watchLater = if (includes(TypeTypeBackupCategory.WATCH_LATER)) watchLater.getAll(userId) else null,
            favorites = if (includes(TypeTypeBackupCategory.FAVORITES)) favorites.getAll(userId) else null,
            progress = if (includes(TypeTypeBackupCategory.PROGRESS)) progress.getAll(userId) else null,
            searchHistory = if (includes(TypeTypeBackupCategory.SEARCH_HISTORY)) searchHistory.getAll(userId) else null,
            savedPlaylists = if (includes(TypeTypeBackupCategory.SAVED_PLAYLISTS)) savedPlaylists.getAll(userId) else null,
            settings = if (includes(TypeTypeBackupCategory.SETTINGS)) settings.get(userId) else null,
            contentFilters = if (includes(TypeTypeBackupCategory.CONTENT_FILTERS)) contentFilters(userId) else null,
        )
    }

    suspend fun restore(userId: String, backup: TypeTypeBackupItem): TypeTypeRestoreSummary {
        require(backup.format == TYPE_TYPE_BACKUP_FORMAT) { "Unsupported backup format" }
        require(backup.version == TYPE_TYPE_BACKUP_VERSION) { "Unsupported backup version" }
        require(backup.categories.isNotEmpty()) { "Backup has no categories" }
        val categories = TypeTypeBackupCategory.parse(backup.categories.joinToString(","))
            ?: throw IllegalArgumentException("Invalid backup categories")
        validateSections(backup, categories)
        validateSubscriptionGroups(backup, categories)
        validateContentFilters(backup, categories)
        return TypeTypeBackupRestoreWriter.restore(userId, backup, categories)
    }

    private suspend fun contentFilters(userId: String) = TypeTypeContentFiltersBackup(
        blockedChannels = blocked.getUserChannels(userId),
        blockedVideos = blocked.getUserVideos(userId),
        blockedKeywords = blocked.getUserKeywords(userId),
        allowedChannels = allowedChannels.getUserChannels(userId),
        allowedPlaylists = allowedPlaylists.getUserPlaylists(userId),
    )
}

private fun validateSections(
    backup: TypeTypeBackupItem,
    categories: Set<TypeTypeBackupCategory>,
) {
    val missing = categories.filter { category ->
        when (category) {
            TypeTypeBackupCategory.SUBSCRIPTIONS -> backup.subscriptions == null
            TypeTypeBackupCategory.HISTORY -> backup.history == null
            TypeTypeBackupCategory.PLAYLISTS -> backup.playlists == null
            TypeTypeBackupCategory.WATCH_LATER -> backup.watchLater == null
            TypeTypeBackupCategory.FAVORITES -> backup.favorites == null
            TypeTypeBackupCategory.PROGRESS -> backup.progress == null
            TypeTypeBackupCategory.SEARCH_HISTORY -> backup.searchHistory == null
            TypeTypeBackupCategory.SAVED_PLAYLISTS -> backup.savedPlaylists == null
            TypeTypeBackupCategory.SETTINGS -> backup.settings == null
            TypeTypeBackupCategory.CONTENT_FILTERS -> backup.contentFilters == null
        }
    }
    require(missing.isEmpty()) { "Backup is missing selected data" }
}

private fun validateSubscriptionGroups(
    backup: TypeTypeBackupItem,
    categories: Set<TypeTypeBackupCategory>,
) {
    val groups = backup.subscriptionGroups ?: return
    require(TypeTypeBackupCategory.SUBSCRIPTIONS in categories) {
        "Subscription groups require the subscriptions category"
    }
    val normalizedNames = groups.map { group ->
        require(group.name == group.name.trim() && group.name.length in 1..SubscriptionGroupsService.MAX_GROUP_NAME_LENGTH) {
            "Subscription group names must contain 1 to 100 characters"
        }
        group.name.lowercase(Locale.ROOT)
    }
    require(normalizedNames.distinct().size == normalizedNames.size) {
        "Backup contains duplicate subscription group names"
    }
    val subscriptions = requireNotNull(backup.subscriptions)
        .mapTo(mutableSetOf()) { ChannelUrlCanonicalizer.canonicalize(it.channelUrl) }
    groups.forEach { group ->
        val channels = group.channelUrls.map(ChannelUrlCanonicalizer::canonicalize)
        require(channels.distinct().size == channels.size) {
            "Backup contains duplicate subscription group memberships"
        }
        require(channels.all { it in subscriptions }) {
            "Subscription group membership references an unknown subscription"
        }
    }
}

private fun validateContentFilters(
    backup: TypeTypeBackupItem,
    categories: Set<TypeTypeBackupCategory>,
) {
    if (TypeTypeBackupCategory.CONTENT_FILTERS !in categories) return
    val keywords = requireNotNull(backup.contentFilters).blockedKeywords
        .map { normalizeBlockedKeyword(it.keyword) }
    require(keywords.all { it.length in 1..100 }) { "Blocked keywords must contain 1 to 100 characters" }
    require(keywords.distinct().size == keywords.size) { "Backup contains duplicate blocked keywords" }
}
