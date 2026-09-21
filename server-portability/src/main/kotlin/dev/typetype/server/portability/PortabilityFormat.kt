package dev.typetype.server.portability

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class PortabilityFormat(val wireName: String) {
    @SerialName("typetype")
    TYPE_TYPE("typetype"),
    @SerialName("pipepipe")
    PIPE_PIPE("pipepipe"),
    @SerialName("newpipe")
    NEW_PIPE("newpipe"),
    @SerialName("invidious")
    INVIDIOUS("invidious"),
    @SerialName("piped")
    PIPED("piped"),
    @SerialName("libretube")
    LIBRE_TUBE("libretube"),
    @SerialName("viewtube")
    VIEW_TUBE("viewtube"),
    @SerialName("materialious")
    MATERIALIOUS("materialious"),
    @SerialName("youtube-local")
    YOUTUBE_LOCAL("youtube-local"),
    @SerialName("flow")
    FLOW("flow"),
    @SerialName("skytube")
    SKY_TUBE("skytube"),
    @SerialName("grayjay")
    GRAYJAY("grayjay"),
    @SerialName("youtube-takeout")
    YOUTUBE_TAKEOUT("youtube-takeout"),
    @SerialName("opml")
    OPML("opml"),
}

@Serializable
enum class PortabilityCategory(val wireName: String) {
    @SerialName("subscriptions")
    SUBSCRIPTIONS("subscriptions"),
    @SerialName("subscriptionGroups")
    SUBSCRIPTION_GROUPS("subscriptionGroups"),
    @SerialName("history")
    HISTORY("history"),
    @SerialName("playlists")
    PLAYLISTS("playlists"),
    @SerialName("watchLater")
    WATCH_LATER("watchLater"),
    @SerialName("favorites")
    FAVORITES("favorites"),
    @SerialName("progress")
    PROGRESS("progress"),
    @SerialName("searchHistory")
    SEARCH_HISTORY("searchHistory"),
    @SerialName("savedPlaylists")
    SAVED_PLAYLISTS("savedPlaylists"),
    @SerialName("settings")
    SETTINGS("settings"),
    @SerialName("contentFilters")
    CONTENT_FILTERS("contentFilters"),
}

@Serializable
enum class PortabilityDirection {
    @SerialName("import")
    IMPORT,
    @SerialName("export")
    EXPORT,
}

@Serializable
data class PortabilityCapability(
    val category: PortabilityCategory,
    val directions: Set<PortabilityDirection>,
    val fidelity: PortabilityFidelity,
)

@Serializable
enum class PortabilityFidelity {
    @SerialName("complete")
    COMPLETE,
    @SerialName("partial")
    PARTIAL,
}

@Serializable
data class PortabilityAdapterDescriptor(
    val format: PortabilityFormat,
    val adapterVersion: Int,
    val capabilities: Set<PortabilityCapability>,
    val defaultExtension: String,
    val contentType: String,
)
