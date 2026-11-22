package dev.typetype.server.services

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult

class YoutubeScopedChannelVideosSource(
    private val delegate: ChannelVideosSource,
    private val recovery: YoutubeEgressRecovery? = null,
) : ChannelVideosSource {
    override suspend fun firstPage(url: String): ExtractionResult<ChannelResponse> =
        if (isYoutubeUrl(url)) {
            recovery.retrying("channel_videos") { YoutubeSessionTokenScope.withoutCredentials { delegate.firstPage(url) } }
        } else {
            delegate.firstPage(url)
        }
}
