package dev.typetype.server.services

import dev.typetype.server.models.YoutubeRemoteBrowserStartResponse

sealed interface YoutubeRemoteBrowserStartResult {
    data class Started(val response: YoutubeRemoteBrowserStartResponse) : YoutubeRemoteBrowserStartResult
    data object Disabled : YoutubeRemoteBrowserStartResult
    data object Misconfigured : YoutubeRemoteBrowserStartResult
    data object AlreadyActive : YoutubeRemoteBrowserStartResult
    data object CapacityReached : YoutubeRemoteBrowserStartResult
    data object TokenUnavailable : YoutubeRemoteBrowserStartResult
}
