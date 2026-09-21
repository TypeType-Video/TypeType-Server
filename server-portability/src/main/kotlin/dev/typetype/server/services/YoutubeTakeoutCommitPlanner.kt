package dev.typetype.server.services

import dev.typetype.server.models.YoutubeTakeoutCommitPlan
import dev.typetype.server.models.YoutubeTakeoutCommitRequest

object YoutubeTakeoutCommitPlanner {
    fun fromRequest(request: YoutubeTakeoutCommitRequest?): YoutubeTakeoutCommitPlan {
        if (request == null) return YoutubeTakeoutCommitPlan(true, true, true, true, true, true)
        return YoutubeTakeoutCommitPlan(
            importSubscriptions = request.importSubscriptions,
            importPlaylists = request.importPlaylists,
            importPlaylistItems = request.importPlaylistItems,
            importFavorites = request.importFavorites,
            importWatchLater = request.importWatchLater,
            importHistory = request.importHistory,
        )
    }
}
