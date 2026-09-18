package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class CachedSuggestionService(
    private val delegate: SuggestionService,
    private val cache: CacheService,
) : SuggestionService {

    private val listSerializer = ListSerializer(String.serializer())

    override suspend fun getSuggestions(query: String, serviceId: Int): ExtractionResult<List<String>> =
        PublicExtractionCache.getOrLoad(
            cache = cache,
            area = "suggestions",
            key = PublicCacheKey.of("suggestions", serviceId.toString(), query),
            serializer = listSerializer,
            ttlSeconds = { PublicCachePolicy.suggestionTtl(serviceId) },
        ) { delegate.getSuggestions(query, serviceId) }
}
