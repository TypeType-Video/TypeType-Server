package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.models.TypeTypeBackupItem
import dev.typetype.server.models.TypeTypeRestoreSummary

object TypeTypeBackupRestoreWriter {
    suspend fun restore(
        userId: String,
        backup: TypeTypeBackupItem,
        categories: Set<TypeTypeBackupCategory>,
    ): TypeTypeRestoreSummary = DatabaseFactory.query {
        SubscriptionMutationLock.acquire(userId)
        val restored = linkedMapOf<String, Int>()
        if (TypeTypeBackupCategory.SUBSCRIPTIONS in categories) {
            restored["subscriptions"] = TypeTypeBackupCoreRestore.subscriptions(
                userId,
                requireNotNull(backup.subscriptions),
            )
            backup.subscriptionGroups?.let { groups ->
                val counts = SubscriptionGroupBackupRepository.restore(userId, groups)
                restored["subscriptionGroups"] = counts.first
                restored["subscriptionGroupMemberships"] = counts.second
            }
        }
        if (TypeTypeBackupCategory.HISTORY in categories) {
            restored["history"] = TypeTypeBackupCoreRestore.history(
                userId,
                requireNotNull(backup.history),
            )
        }
        if (TypeTypeBackupCategory.PLAYLISTS in categories) {
            val counts = TypeTypeBackupCoreRestore.playlists(
                userId,
                requireNotNull(backup.playlists),
            )
            restored["playlists"] = counts.first
            restored["playlistVideos"] = counts.second
        }
        if (TypeTypeBackupCategory.WATCH_LATER in categories) {
            restored["watchLater"] = TypeTypeBackupLibraryRestore.watchLater(
                userId,
                requireNotNull(backup.watchLater),
            )
        }
        if (TypeTypeBackupCategory.FAVORITES in categories) {
            restored["favorites"] = TypeTypeBackupLibraryRestore.favorites(
                userId,
                requireNotNull(backup.favorites),
            )
        }
        if (TypeTypeBackupCategory.PROGRESS in categories) {
            restored["progress"] = TypeTypeBackupLibraryRestore.progress(
                userId,
                requireNotNull(backup.progress),
            )
        }
        if (TypeTypeBackupCategory.SEARCH_HISTORY in categories) {
            restored["searchHistory"] = TypeTypeBackupLibraryRestore.searchHistory(
                userId,
                requireNotNull(backup.searchHistory),
            )
        }
        if (TypeTypeBackupCategory.SAVED_PLAYLISTS in categories) {
            restored["savedPlaylists"] = TypeTypeBackupLibraryRestore.savedPlaylists(
                userId,
                requireNotNull(backup.savedPlaylists),
            )
        }
        if (TypeTypeBackupCategory.SETTINGS in categories) {
            restored["settings"] = TypeTypeBackupLibraryRestore.settings(
                userId,
                requireNotNull(backup.settings),
            )
        }
        if (TypeTypeBackupCategory.CONTENT_FILTERS in categories) {
            restored.putAll(
                TypeTypeBackupFilterRestore.restore(
                    userId,
                    requireNotNull(backup.contentFilters),
                ),
            )
        }
        TypeTypeRestoreSummary(restored)
    }
}
