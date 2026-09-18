package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe

class PipePipeSuggestionService : SuggestionService {

    override suspend fun getSuggestions(query: String, serviceId: Int): ExtractionResult<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                withExtractionRetry {
                    withTimeout(10_000L) {
                        NewPipe.getService(serviceId).suggestionExtractor.suggestionList(query)
                    }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Suggestion extraction failed") }
            )
        }
}
