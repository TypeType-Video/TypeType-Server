package dev.typetype.server.portability

internal object TypeTypePortabilityImport {
    fun write(
        userId: String,
        category: PortabilityCategory,
        source: PortabilityRecordSource,
        policy: PortabilityDuplicatePolicy,
        onRecord: () -> Unit,
    ): Long = when (category) {
        PortabilityCategory.SUBSCRIPTIONS,
        PortabilityCategory.SUBSCRIPTION_GROUPS,
        PortabilityCategory.PLAYLISTS,
        -> TypeTypePortabilityCoreImport.write(userId, category, source, policy, onRecord)
        PortabilityCategory.HISTORY -> TypeTypePortabilityHistoryImport.write(userId, source, policy, onRecord)
        PortabilityCategory.WATCH_LATER,
        PortabilityCategory.FAVORITES,
        PortabilityCategory.PROGRESS,
        PortabilityCategory.SEARCH_HISTORY,
        PortabilityCategory.SAVED_PLAYLISTS,
        -> TypeTypePortabilityLibraryImport.write(userId, category, source, policy, onRecord)
        PortabilityCategory.SETTINGS -> TypeTypePortabilitySettingsImport.write(userId, source, onRecord)
        PortabilityCategory.CONTENT_FILTERS -> TypeTypePortabilityFilterImport.write(userId, source, policy, onRecord)
    }
}
