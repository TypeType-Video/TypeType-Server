package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult

interface SuggestionService {
    suspend fun getSuggestions(query: String, serviceId: Int): ExtractionResult<List<String>>
}
