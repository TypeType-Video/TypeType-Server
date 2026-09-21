package dev.typetype.server.services

object YoutubeTakeoutFactory {
    fun create(
        subscriptionsService: SubscriptionsService,
        playlistService: PlaylistService,
        historyService: HistoryService,
        favoritesService: FavoritesService,
        watchLaterService: WatchLaterService,
    ): YoutubeTakeoutImportJobService {
        val previewLookup = YoutubeTakeoutPreviewLookupService(historyService, favoritesService, watchLaterService)
        val signalImport = YoutubeTakeoutSignalImportService(favoritesService, watchLaterService, historyService)
        return YoutubeTakeoutImportJobService(
            parser = YoutubeTakeoutParserService(),
            previewService = YoutubeTakeoutPreviewService(subscriptionsService, playlistService, previewLookup),
            importerService = YoutubeTakeoutImporterService(subscriptionsService, playlistService, signalImport, YoutubeTakeoutPlaylistKeyService()),
            store = YoutubeTakeoutImportJobStore(),
            statusStore = YoutubeTakeoutImportJobStatusStore(),
            archiveStore = YoutubeTakeoutImportJobArchiveStore(),
            reportStore = YoutubeTakeoutImportJobReportStore(),
            flagsStore = YoutubeTakeoutImportJobFlagsStore(),
            cache = YoutubeTakeoutImportCache(),
        )
    }
}
