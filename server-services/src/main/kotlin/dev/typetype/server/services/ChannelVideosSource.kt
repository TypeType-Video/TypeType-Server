package dev.typetype.server.services

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult

fun interface ChannelVideosSource {
    suspend fun firstPage(url: String): ExtractionResult<ChannelResponse>
}
