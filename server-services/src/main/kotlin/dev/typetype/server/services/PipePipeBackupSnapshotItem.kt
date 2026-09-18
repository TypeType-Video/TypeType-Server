package dev.typetype.server.services

data class PipePipeBackupSnapshotItem(
    val subscriptions: List<PipePipeBackupSubscriptionItem>,
    val history: List<PipePipeBackupHistoryItem>,
    val playlists: List<PipePipeBackupPlaylistItem>,
    val progress: List<PipePipeBackupProgressItem>,
    val searchHistory: List<PipePipeBackupSearchHistoryItem>,
)
