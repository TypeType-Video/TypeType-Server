package dev.typetype.server.services

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult

class ChannelServiceVideosSource(private val channelService: ChannelService) : ChannelVideosSource {
    override suspend fun firstPage(url: String): ExtractionResult<ChannelResponse> = channelService.getChannel(url, null)
}
