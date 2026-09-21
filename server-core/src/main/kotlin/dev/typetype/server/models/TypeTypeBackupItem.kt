package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class TypeTypeBackupItem(
    val format: String = TYPE_TYPE_BACKUP_FORMAT,
    val version: Int = TYPE_TYPE_BACKUP_VERSION,
    val exportedAt: Long,
    val categories: List<String>,
    val subscriptions: List<SubscriptionItem>? = null,
    val subscriptionGroups: List<SubscriptionGroupBackupItem>? = null,
    val history: List<HistoryItem>? = null,
    val playlists: List<PlaylistItem>? = null,
    val watchLater: List<WatchLaterItem>? = null,
    val favorites: List<FavoriteItem>? = null,
    val progress: List<ProgressItem>? = null,
    val searchHistory: List<SearchHistoryItem>? = null,
    val savedPlaylists: List<SavedPlaylistItem>? = null,
    val settings: SettingsItem? = null,
    val contentFilters: TypeTypeContentFiltersBackup? = null,
)

@Serializable
data class TypeTypeContentFiltersBackup(
    val blockedChannels: List<BlockedItem> = emptyList(),
    val blockedVideos: List<BlockedItem> = emptyList(),
    val blockedKeywords: List<BlockedKeywordItem> = emptyList(),
    val allowedChannels: List<AllowedChannelItem> = emptyList(),
    val allowedPlaylists: List<AllowedPlaylistItem> = emptyList(),
)

@Serializable
data class TypeTypeRestoreSummary(
    val restored: Map<String, Int>,
)

const val TYPE_TYPE_BACKUP_FORMAT = "typetype-backup"
const val TYPE_TYPE_BACKUP_VERSION = 1
