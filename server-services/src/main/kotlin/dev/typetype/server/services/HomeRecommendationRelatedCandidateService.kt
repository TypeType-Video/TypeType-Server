package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class HomeRecommendationRelatedCandidateService(
    private val streamService: StreamService,
) {
    suspend fun fetch(
        seedUrls: List<String>,
        source: HomeRecommendationSourceTag,
        seedLimit: Int,
        relatedPerSeedLimit: Int,
    ): List<HomeRecommendationTaggedVideo> = coroutineScope {
        seedUrls.asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .take(seedLimit)
            .map { seed -> async { related(seed, source, relatedPerSeedLimit) } }
            .toList()
            .awaitAll()
            .flatten()
    }

    private suspend fun related(
        seedUrl: String,
        source: HomeRecommendationSourceTag,
        relatedPerSeedLimit: Int,
    ): List<HomeRecommendationTaggedVideo> =
        when (val result = streamService.getStreamInfo(seedUrl)) {
            is ExtractionResult.Success -> result.data.relatedStreams
                .asSequence()
                .filter { it.url.isNotBlank() }
                .filterNot { it.url == seedUrl }
                .take(relatedPerSeedLimit)
                .map { HomeRecommendationTaggedVideo(it, source) }
                .toList()
            is ExtractionResult.BadRequest -> emptyList()
            is ExtractionResult.Failure -> emptyList()
        }
}
