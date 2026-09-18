package dev.typetype.server.services

sealed interface YoutubeRemoteBrowserReserveResult {
    data class Reserved(val session: YoutubeRemoteBrowserSession, val wsToken: String) : YoutubeRemoteBrowserReserveResult
    data object AlreadyActive : YoutubeRemoteBrowserReserveResult
    data object CapacityReached : YoutubeRemoteBrowserReserveResult
}
