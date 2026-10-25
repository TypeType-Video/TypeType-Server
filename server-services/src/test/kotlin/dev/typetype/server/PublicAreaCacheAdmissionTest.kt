package dev.typetype.server

import dev.typetype.server.models.CommentsPageResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PublicPlaylistItem
import dev.typetype.server.models.PublicPlaylistResponse
import dev.typetype.server.models.SearchFiltersResponse
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.CachedCommentService
import dev.typetype.server.services.CachedPublicPlaylistService
import dev.typetype.server.services.CachedSearchService
import dev.typetype.server.services.CachedSuggestionService
import dev.typetype.server.services.CachedTrendingService
import dev.typetype.server.services.CommentService
import dev.typetype.server.services.PublicCacheKey
import dev.typetype.server.services.PublicPlaylistService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.SuggestionService
import dev.typetype.server.services.TrendingService
import dev.typetype.server.services.YOUTUBE_SERVICE_ID
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PublicAreaCacheAdmissionTest {
    private val cache = ExpiringFakeCache()
    private val youtube = YOUTUBE_SERVICE_ID.toString()

    @Test
    fun `empty trending results are kept for thirty seconds`() = runTest {
        val service = CachedTrendingService(
            object : TrendingService {
                override suspend fun getTrending(serviceId: Int): ExtractionResult<List<VideoItem>> =
                    ExtractionResult.Success(emptyList())
            },
            cache,
        )

        service.getTrending(YOUTUBE_SERVICE_ID)

        assertEquals(30L, cache.ttls[PublicCacheKey.of("trending", youtube)])
    }

    @Test
    fun `empty suggestions are kept for thirty seconds`() = runTest {
        val service = CachedSuggestionService(
            object : SuggestionService {
                override suspend fun getSuggestions(query: String, serviceId: Int): ExtractionResult<List<String>> =
                    ExtractionResult.Success(emptyList())
            },
            cache,
        )

        service.getSuggestions("rick", YOUTUBE_SERVICE_ID)

        assertEquals(30L, cache.ttls[PublicCacheKey.of("suggestions", youtube, "rick")])
    }

    @Test
    fun `empty first search pages are kept for thirty seconds`() = runTest {
        val service = CachedSearchService(
            object : SearchService {
                override suspend fun search(
                    query: String,
                    serviceId: Int,
                    nextpage: String?,
                    contentFilter: String?,
                    filters: List<String>,
                ): ExtractionResult<SearchPageResponse> =
                    ExtractionResult.Success(SearchPageResponse(emptyList(), null, null, false))

                override suspend fun filters(serviceId: Int, contentFilter: String?): ExtractionResult<SearchFiltersResponse> =
                    error("unused")
            },
            cache,
        )

        service.search("rick", YOUTUBE_SERVICE_ID, null, null, emptyList())

        assertEquals(30L, cache.ttls[PublicCacheKey.of("search-v4", youtube, "rick", null, null)])
    }

    @Test
    fun `empty first comment pages are kept for thirty seconds unless comments are disabled`() = runTest {
        var disabled = false
        val service = CachedCommentService(
            object : CommentService {
                override suspend fun getComments(url: String, nextpage: String?): ExtractionResult<CommentsPageResponse> =
                    ExtractionResult.Success(CommentsPageResponse(emptyList(), null, disabled))
            },
            cache,
        )

        service.getComments("https://youtube.com/watch?v=a", null)
        disabled = true
        service.getComments("https://youtube.com/watch?v=b", null)

        assertEquals(30L, cache.ttls[PublicCacheKey.of("comments", "https://youtube.com/watch?v=a", null)])
        assertEquals(180L, cache.ttls[PublicCacheKey.of("comments", "https://youtube.com/watch?v=b", null)])
    }

    @Test
    fun `empty first playlist pages are kept for thirty seconds`() = runTest {
        val playlist = PublicPlaylistItem("id", "title", "url", "", "uploader", 0L, "normal")
        val service = CachedPublicPlaylistService(
            object : PublicPlaylistService {
                override suspend fun getPlaylist(url: String, nextpage: String?): ExtractionResult<PublicPlaylistResponse> =
                    ExtractionResult.Success(PublicPlaylistResponse(playlist, emptyList(), null))
            },
            cache,
        )

        service.getPlaylist("https://youtube.com/playlist?list=x", null)

        assertEquals(30L, cache.ttls[PublicCacheKey.of("playlist", "https://youtube.com/playlist?list=x", null)])
    }
}
