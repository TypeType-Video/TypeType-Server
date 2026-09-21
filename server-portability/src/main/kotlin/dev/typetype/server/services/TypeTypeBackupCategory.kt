package dev.typetype.server.services

enum class TypeTypeBackupCategory(val wireName: String) {
    SUBSCRIPTIONS("subscriptions"),
    HISTORY("history"),
    PLAYLISTS("playlists"),
    WATCH_LATER("watchLater"),
    FAVORITES("favorites"),
    PROGRESS("progress"),
    SEARCH_HISTORY("searchHistory"),
    SAVED_PLAYLISTS("savedPlaylists"),
    SETTINGS("settings"),
    CONTENT_FILTERS("contentFilters");

    companion object {
        val all: Set<TypeTypeBackupCategory> = entries.toSet()

        fun parse(raw: String?): Set<TypeTypeBackupCategory>? {
            if (raw.isNullOrBlank() || raw == "all") return all
            val requested = raw.split(',').map(String::trim).filter(String::isNotEmpty)
            if (requested.isEmpty()) return null
            val byName = entries.associateBy(TypeTypeBackupCategory::wireName)
            return requested.map { byName[it] ?: return null }.toSet()
        }
    }
}
