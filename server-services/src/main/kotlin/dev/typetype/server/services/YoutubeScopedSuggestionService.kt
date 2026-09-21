package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult

class YoutubeScopedSuggestionService(private val delegate: SuggestionService) : SuggestionService {
    override suspend fun getSuggestions(query: String, serviceId: Int): ExtractionResult<List<String>> =
        if (serviceId == YOUTUBE_SERVICE_ID) {
            YoutubeSessionTokenScope.withoutCredentials { delegate.getSuggestions(query, serviceId) }
        } else {
            delegate.getSuggestions(query, serviceId)
        }
}
