package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationCandidateService
import dev.typetype.server.services.HomeRecommendationPoolMode
import dev.typetype.server.services.HomeRecommendationProfile
import dev.typetype.server.services.HomeRecommendationSignalContext
import dev.typetype.server.services.HomeRecommendationSourceTag
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionShortsFeedService
import dev.typetype.server.services.TrendingService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeRecommendationCandidateServiceTest {
    private val subscriptionFeedService: SubscriptionFeedService = mockk()
    private val subscriptionShortsFeedService: SubscriptionShortsFeedService = mockk()
    private val streamService: StreamService = mockk()
    private val trendingService: TrendingService = mockk()
    private val service = HomeRecommendationCandidateService(
        subscriptionFeedService, subscriptionShortsFeedService, streamService, trendingService,
    )

    @BeforeEach
    fun setup() {
        coEvery { subscriptionFeedService.getCachedFeed(any(), any(), any()) } returns SubscriptionFeedResponse(emptyList(), null)
        coEvery { subscriptionFeedService.getFeed(any(), any(), any()) } returns SubscriptionFeedResponse(emptyList(), null)
        coEvery { subscriptionShortsFeedService.getBlendedFeed(any(), any(), any(), any()) } returns SubscriptionFeedResponse(emptyList(), null)
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Failure("none")
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(emptyList())
    }

    @Test
    fun `fast mode stays cache-only without related streams`() = runTest {
        val seed = video("s1", "seed")
        coEvery { subscriptionFeedService.getCachedFeed(any(), any(), any()) } returns SubscriptionFeedResponse(listOf(seed), null)
        val pool = service.fetchCandidates("u", 0, profile(), HomeRecommendationPoolMode.FAST)
        assertTrue(pool.subscriptions.any { it.video.id == "s1" && it.source == HomeRecommendationSourceTag.SUBSCRIPTION })
        assertTrue(pool.discovery.isEmpty())
    }

    @Test
    fun `favorite seeds add exploration-tagged related discovery`() = runTest {
        val favoriteSeed = video("f1", "favorite")
        val related = video("rf1", "related")
        coEvery { streamService.getStreamInfo(favoriteSeed.url) } returns ExtractionResult.Success(stream(favoriteSeed.url, listOf(related)))
        val signalContext = HomeRecommendationSignalContext(favoriteUrls = listOf(favoriteSeed.url))
        val pool = service.fetchCandidates("u", 0, profile(), HomeRecommendationPoolMode.FULL, signalContext)
        assertTrue(pool.discovery.any { it.video.id == "rf1" && it.source == HomeRecommendationSourceTag.DISCOVERY_EXPLORATION })
    }

    @Test
    fun `no subscription or favorite seeds keeps discovery empty`() = runTest {
        val pool = service.fetchCandidates("u", 0, profile(), HomeRecommendationPoolMode.FAST)
        assertTrue(pool.discovery.isEmpty())
    }

    @Test
    fun `bilibili mode excludes subscriptions from other services`() = runTest {
        val youtube = video("yt", "YouTube")
        val bilibili = video("bili", "BiliBili", "https://www.bilibili.com/video/BV1234567890")
        coEvery { subscriptionFeedService.getCachedFeed(any(), any(), any()) } returns
            SubscriptionFeedResponse(listOf(youtube, bilibili), null)

        val pool = service.fetchCandidates("u", 5, profile(), HomeRecommendationPoolMode.FAST)

        assertTrue(pool.subscriptions.map { it.video.id } == listOf("bili"))
    }

    @Test
    fun `niconico mode falls back to niconico trending videos`() = runTest {
        val youtube = video("yt", "YouTube")
        val niconico = video("nico", "NicoNico", "https://www.nicovideo.jp/watch/sm123")
        coEvery { subscriptionFeedService.getCachedFeed(any(), any(), any()) } returns
            SubscriptionFeedResponse(listOf(youtube), null)
        coEvery { trendingService.getTrending(6) } returns ExtractionResult.Success(listOf(youtube, niconico))

        val pool = service.fetchCandidates("u", 6, profile(), HomeRecommendationPoolMode.FAST)

        assertTrue(pool.subscriptions.isEmpty())
        assertTrue(pool.discovery.map { it.video.id } == listOf("nico"))
    }

    private fun profile(): HomeRecommendationProfile = HomeRecommendationProfile(
        seenUrls = emptySet(), blockedVideos = emptySet(), blockedChannels = emptySet(),
        feedbackBlockedVideos = emptySet(), feedbackBlockedChannels = emptySet(),
        subscriptionChannels = emptySet(), favoriteUrls = emptySet(), watchLaterUrls = emptySet(),
        keywordAffinity = emptySet(), themeTokens = emptySet(), themeQueries = emptyList(),
        channelInterest = emptyMap(), topicInterest = emptyMap(),
    )

    private fun stream(seedUrl: String, related: List<VideoItem>): StreamResponse = StreamResponse(
        id = "id", title = "t", uploaderName = "u", uploaderUrl = "c", uploaderAvatarUrl = "", thumbnailUrl = "",
        description = "", duration = 1, viewCount = 0, likeCount = 0, dislikeCount = 0, uploadDate = "", uploaded = 0,
        uploaderSubscriberCount = 0, uploaderVerified = false, category = "", license = "", visibility = "",
        tags = emptyList(), streamType = "video_stream", isShortFormContent = false, requiresMembership = false,
        startPosition = 0, streamSegments = emptyList(), hlsUrl = "", dashMpdUrl = "", videoStreams = emptyList(),
        audioStreams = emptyList(), originalAudioTrackId = null, preferredDefaultAudioTrackId = null,
        videoOnlyStreams = emptyList(), subtitles = emptyList(), previewFrames = emptyList(),
        sponsorBlockSegments = emptyList(), relatedStreams = related, publishedAt = 0,
    )

    private fun video(id: String, title: String, url: String = "https://yt.com/v/$id"): VideoItem = VideoItem(
        id = id, title = title, url = url, thumbnailUrl = "", uploaderName = "channel",
        uploaderUrl = "https://yt.com/c/channel", uploaderAvatarUrl = "", duration = 60, viewCount = 0,
        uploadDate = "", uploaded = System.currentTimeMillis(), streamType = "video_stream", isShortFormContent = false,
        uploaderVerified = false, shortDescription = null,
    )
}
