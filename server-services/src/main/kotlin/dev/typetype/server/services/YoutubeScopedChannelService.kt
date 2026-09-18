package dev.typetype.server.services

import dev.typetype.server.models.ChannelPlaylistsResponse
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult

class YoutubeScopedChannelService(private val delegate: ChannelService) : ChannelService {
    override suspend fun getChannel(url: String, nextpage: String?, sort: String?): ExtractionResult<ChannelResponse> =
        if (isYoutubeUrl(url)) {
            YoutubeSessionTokenScope.withoutCredentials { delegate.getChannel(url, nextpage, sort) }
        } else {
            delegate.getChannel(url, nextpage, sort)
        }

    override suspend fun getPlaylists(url: String, nextpage: String?): ExtractionResult<ChannelPlaylistsResponse> =
        if (isYoutubeUrl(url)) {
            YoutubeSessionTokenScope.withoutCredentials { delegate.getPlaylists(url, nextpage) }
        } else {
            delegate.getPlaylists(url, nextpage)
        }
}
