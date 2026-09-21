package dev.typetype.server.services

class YoutubeTakeoutPreviewLookupService(
    private val historyService: HistoryService,
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
) {
    suspend fun historyKeys(userId: String): Set<Pair<String, Long>> {
        return historyService.dedupKeys(userId)
    }

    suspend fun favorites(userId: String): Set<String> {
        return favoritesService.getAll(userId).map { it.videoUrl }.toSet()
    }

    suspend fun watchLater(userId: String): Set<String> {
        return watchLaterService.getAll(userId).map { it.url }.toSet()
    }
}
