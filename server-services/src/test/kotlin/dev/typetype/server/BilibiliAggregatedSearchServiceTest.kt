package dev.typetype.server

import dev.typetype.server.models.ChannelResultItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchFiltersResponse
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.BILIBILI_SERVICE_ID
import dev.typetype.server.services.BilibiliAggregatedSearchService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.YOUTUBE_SERVICE_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BilibiliAggregatedSearchServiceTest {

    private fun sampleChannel(id: String, name: String, subs: Long) = ChannelResultItem(
        id = id,
        name = name,
        url = "https://space.bilibili.com/$id",
        thumbnailUrl = "https://example.com/$id.png",
        description = "Bio for $name",
        subscriberCount = subs,
        streamCount = 100,
        isVerified = false,
    )

    private fun sampleVideo(id: String, title: String) = VideoItem(
        id = id,
        title = title,
        url = "https://www.bilibili.com/video/$id",
        thumbnailUrl = "https://example.com/v.png",
        uploaderName = "Creator",
        uploaderUrl = "https://space.bilibili.com/1",
        uploaderAvatarUrl = "https://example.com/avatar.png",
        duration = 120,
        viewCount = 1000,
        uploadDate = "1 day ago",
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = "Sample description",
    )

    private class FakeSearchService : SearchService {
        val calls = mutableListOf<SearchCall>()
        var channelDelayMs = 0L
        var channelError: Throwable? = null

        var videoResponse: ExtractionResult<SearchPageResponse> = ExtractionResult.Success(
            SearchPageResponse(
                items = emptyList(),
                nextpage = null,
                searchSuggestion = null,
                isCorrectedSearch = false,
            )
        )

        var channelResponse: ExtractionResult<SearchPageResponse> = ExtractionResult.Success(
            SearchPageResponse(
                items = emptyList(),
                nextpage = null,
                searchSuggestion = null,
                isCorrectedSearch = false,
            )
        )

        data class SearchCall(
            val query: String,
            val serviceId: Int,
            val nextpage: String?,
            val contentFilter: String?,
            val filters: List<String>,
        )

        override suspend fun search(
            query: String,
            serviceId: Int,
            nextpage: String?,
            contentFilter: String?,
            filters: List<String>,
        ): ExtractionResult<SearchPageResponse> {
            calls.add(SearchCall(query, serviceId, nextpage, contentFilter, filters))
            return if (contentFilter == BilibiliAggregatedSearchService.BILIBILI_CHANNELS_FILTER) {
                channelError?.let { throw it }
                if (channelDelayMs > 0) delay(channelDelayMs)
                channelResponse
            } else {
                videoResponse
            }
        }

        override suspend fun filters(
            serviceId: Int,
            contentFilter: String?,
        ): ExtractionResult<SearchFiltersResponse> = ExtractionResult.Success(
            SearchFiltersResponse(emptyList(), emptyList(), emptyList())
        )
    }

    @Test
    fun `default bilibili search coordinates video and channel search and takes top creator`() = runTest {
        val fake = FakeSearchService()
        val video = sampleVideo("BV1", "Test Video")
        val channel1 = sampleChannel("1001", "Top UP", 5_000_000)
        val channel2 = sampleChannel("1002", "Second UP", 20_000)

        fake.videoResponse = ExtractionResult.Success(
            SearchPageResponse(
                items = listOf(video),
                nextpage = "page2_cursor",
                searchSuggestion = null,
                isCorrectedSearch = false,
                channels = emptyList(),
            )
        )
        fake.channelResponse = ExtractionResult.Success(
            SearchPageResponse(
                items = emptyList(),
                nextpage = null,
                searchSuggestion = null,
                isCorrectedSearch = false,
                channels = listOf(channel1, channel2),
            )
        )

        val aggregated = BilibiliAggregatedSearchService(fake)
        val result = aggregated.search("影视飓风", BILIBILI_SERVICE_ID)

        assertTrue(result is ExtractionResult.Success)
        val data = (result as ExtractionResult.Success).data

        // Videos are preserved
        assertEquals(1, data.items.size)
        assertEquals("BV1", data.items.first().id)
        // Only the top 1 creator is taken to serve as top creator banner
        assertEquals(1, data.channels.size)
        assertEquals("1001", data.channels.first().id)
        assertEquals("Top UP", data.channels.first().name)

        // Both calls were made
        assertEquals(2, fake.calls.size)
        assertTrue(fake.calls.any { it.contentFilter == null })
        assertTrue(fake.calls.any { it.contentFilter == BilibiliAggregatedSearchService.BILIBILI_CHANNELS_FILTER })
    }

    @Test
    fun `bilibili pagination does not query channels`() = runTest {
        val fake = FakeSearchService()
        val aggregated = BilibiliAggregatedSearchService(fake)

        aggregated.search("test", BILIBILI_SERVICE_ID, nextpage = "cursor_abc")

        assertEquals(1, fake.calls.size)
        assertEquals("cursor_abc", fake.calls.first().nextpage)
        assertEquals(null, fake.calls.first().contentFilter)
    }

    @Test
    fun `explicit content filter bypasses aggregation`() = runTest {
        val fake = FakeSearchService()
        val aggregated = BilibiliAggregatedSearchService(fake)

        aggregated.search("test", BILIBILI_SERVICE_ID, contentFilter = "|2|channels")

        assertEquals(1, fake.calls.size)
        assertEquals("|2|channels", fake.calls.first().contentFilter)
    }

    @Test
    fun `non-bilibili service bypasses aggregation`() = runTest {
        val fake = FakeSearchService()
        val aggregated = BilibiliAggregatedSearchService(fake)

        aggregated.search("test", YOUTUBE_SERVICE_ID)

        assertEquals(1, fake.calls.size)
        assertEquals(YOUTUBE_SERVICE_ID, fake.calls.first().serviceId)
    }

    @Test
    fun `channel query failure gracefully degrades to empty channels`() = runTest {
        val fake = FakeSearchService()
        fake.videoResponse = ExtractionResult.Success(
            SearchPageResponse(
                items = listOf(sampleVideo("BV1", "Test Video")),
                nextpage = null,
                searchSuggestion = null,
                isCorrectedSearch = false,
            )
        )
        fake.channelResponse = ExtractionResult.Failure("Network error")

        val aggregated = BilibiliAggregatedSearchService(fake)
        val result = aggregated.search("test", BILIBILI_SERVICE_ID)

        assertTrue(result is ExtractionResult.Success)
        val data = (result as ExtractionResult.Success).data
        assertEquals(1, data.items.size)
        assertTrue(data.channels.isEmpty())
    }

    @Test
    fun `slow channel query times out and preserves video results`() = runTest {
        val fake = FakeSearchService()
        fake.videoResponse = ExtractionResult.Success(
            SearchPageResponse(
                items = listOf(sampleVideo("BV1", "Test Video")),
                nextpage = null,
                searchSuggestion = null,
                isCorrectedSearch = false,
            )
        )
        fake.channelResponse = ExtractionResult.Success(
            SearchPageResponse(
                items = emptyList(),
                nextpage = null,
                searchSuggestion = null,
                isCorrectedSearch = false,
                channels = listOf(sampleChannel("1001", "Slow UP", 5_000_000)),
            )
        )
        fake.channelDelayMs = BilibiliAggregatedSearchService.BILIBILI_CHANNELS_TIMEOUT_MS + 1

        val aggregated = BilibiliAggregatedSearchService(fake)
        val result = aggregated.search("test", BILIBILI_SERVICE_ID)

        assertTrue(result is ExtractionResult.Success)
        val data = (result as ExtractionResult.Success).data
        assertEquals(1, data.items.size)
        assertTrue(data.channels.isEmpty())
        assertEquals(
            BilibiliAggregatedSearchService.BILIBILI_CHANNELS_TIMEOUT_MS,
            testScheduler.currentTime,
        )
    }

    @Test
    fun `channel cancellation is rethrown`() = runTest {
        val fake = FakeSearchService()
        fake.channelError = CancellationException("cancelled")

        val aggregated = BilibiliAggregatedSearchService(fake)

        var cancelled = false
        try {
            aggregated.search("test", BILIBILI_SERVICE_ID)
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
    }
}
