package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PublicPlaylistResponse

interface PublicPlaylistService {
    suspend fun getPlaylist(url: String, nextpage: String?): ExtractionResult<PublicPlaylistResponse>
}
