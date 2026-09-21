package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PodcastEpisodesResponse
import dev.typetype.server.models.PodcastPageResponse

class CachedPodcastService(
    private val delegate: PodcastService,
    private val cache: CacheService,
) : PodcastService {

    companion object {
        private const val PODCAST_CACHE_TTL_SECONDS = 1800L
    }

    override suspend fun getPodcasts(url: String, nextpage: String?): ExtractionResult<PodcastPageResponse> {
        val key = "podcasts:$url:${nextpage ?: "null"}"
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            return runCatching { ExtractionResult.Success(CacheJson.decodeFromString<PodcastPageResponse>(cached)) }.getOrElse {
                delegate.getPodcasts(url, nextpage)
            }
        }
        val result = delegate.getPodcasts(url, nextpage)
        if (result is ExtractionResult.Success) {
            runCatching { cache.set(key, CacheJson.encodeToString(PodcastPageResponse.serializer(), result.data), PODCAST_CACHE_TTL_SECONDS) }
        }
        return result
    }

    override suspend fun getPodcastEpisodes(url: String, nextpage: String?): ExtractionResult<PodcastEpisodesResponse> {
        val key = "podcast-episodes:$url:${nextpage ?: "null"}"
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            return runCatching { ExtractionResult.Success(CacheJson.decodeFromString<PodcastEpisodesResponse>(cached)) }.getOrElse {
                delegate.getPodcastEpisodes(url, nextpage)
            }
        }
        val result = delegate.getPodcastEpisodes(url, nextpage)
        if (result is ExtractionResult.Success) {
            runCatching { cache.set(key, CacheJson.encodeToString(PodcastEpisodesResponse.serializer(), result.data), PODCAST_CACHE_TTL_SECONDS) }
        }
        return result
    }
}
