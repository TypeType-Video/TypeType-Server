package dev.typetype.server.portability

internal object TypeTypePortabilityExport {
    fun write(userId: String, category: PortabilityCategory, sink: PortabilityRecordSink) {
        when (category) {
            PortabilityCategory.SUBSCRIPTIONS,
            PortabilityCategory.SUBSCRIPTION_GROUPS,
            PortabilityCategory.HISTORY,
            PortabilityCategory.PLAYLISTS,
            -> TypeTypePortabilityCoreExport.write(userId, category, sink)
            PortabilityCategory.WATCH_LATER,
            PortabilityCategory.FAVORITES,
            PortabilityCategory.PROGRESS,
            PortabilityCategory.SEARCH_HISTORY,
            PortabilityCategory.SAVED_PLAYLISTS,
            -> TypeTypePortabilityLibraryExport.write(userId, category, sink)
            PortabilityCategory.SETTINGS -> TypeTypePortabilitySettingsExport.write(userId, sink)
            PortabilityCategory.CONTENT_FILTERS -> TypeTypePortabilityFilterExport.write(userId, sink)
        }
    }
}
