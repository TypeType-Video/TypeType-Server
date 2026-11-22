package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PublicPlaylistResponse

class YoutubeScopedPublicPlaylistService(
    private val delegate: PublicPlaylistService,
    private val recovery: YoutubeEgressRecovery? = null,
) : PublicPlaylistService {
    override suspend fun getPlaylist(url: String, nextpage: String?): ExtractionResult<PublicPlaylistResponse> =
        if (isYoutubeUrl(url)) {
            recovery.retrying("playlist") { YoutubeSessionTokenScope.withoutCredentials { delegate.getPlaylist(url, nextpage) } }
        } else {
            delegate.getPlaylist(url, nextpage)
        }
}
