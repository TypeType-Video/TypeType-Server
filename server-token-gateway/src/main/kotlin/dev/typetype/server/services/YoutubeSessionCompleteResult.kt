package dev.typetype.server.services

sealed interface YoutubeSessionCompleteResult {
    data object Completed : YoutubeSessionCompleteResult
    data object InvalidCode : YoutubeSessionCompleteResult
    data object ExpiredCode : YoutubeSessionCompleteResult
    data object InvalidCredentials : YoutubeSessionCompleteResult
    data object Unavailable : YoutubeSessionCompleteResult
}
