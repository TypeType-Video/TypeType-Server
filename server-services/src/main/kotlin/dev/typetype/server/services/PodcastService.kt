package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PodcastEpisodesResponse
import dev.typetype.server.models.PodcastPageResponse

interface PodcastService {
    suspend fun getPodcasts(url: String, nextpage: String?): ExtractionResult<PodcastPageResponse>
    suspend fun getPodcastEpisodes(url: String, nextpage: String?): ExtractionResult<PodcastEpisodesResponse>
}
