package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PublicPlaylistResponse

class YoutubeScopedPublicPlaylistService(private val delegate: PublicPlaylistService) : PublicPlaylistService {
    override suspend fun getPlaylist(url: String, nextpage: String?): ExtractionResult<PublicPlaylistResponse> =
        if (isYoutubeUrl(url)) {
            YoutubeSessionTokenScope.withoutCredentials { delegate.getPlaylist(url, nextpage) }
        } else {
            delegate.getPlaylist(url, nextpage)
        }
}
