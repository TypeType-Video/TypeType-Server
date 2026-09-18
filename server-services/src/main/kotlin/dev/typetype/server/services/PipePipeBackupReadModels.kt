package dev.typetype.server.services

data class PipePipeBackupSubscriptionItem(
    val serviceId: Int,
    val url: String,
    val name: String,
    val avatarUrl: String,
)

data class PipePipeBackupHistoryItem(
    val watchedAt: Long,
    val url: String,
    val title: String,
    val duration: Long,
    val uploader: String,
    val uploaderUrl: String,
    val thumbnail: String,
)

data class PipePipeBackupPlaylistItem(
    val name: String,
    val videos: List<PipePipeBackupPlaylistVideoItem>,
)

data class PipePipeBackupPlaylistVideoItem(
    val url: String,
    val title: String,
    val duration: Long,
    val thumbnail: String,
    val position: Int,
)

data class PipePipeBackupProgressItem(
    val videoUrl: String,
    val position: Long,
)

data class PipePipeBackupSearchHistoryItem(
    val term: String,
    val searchedAt: Long,
)
