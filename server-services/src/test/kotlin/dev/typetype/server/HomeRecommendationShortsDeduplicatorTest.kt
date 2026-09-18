package dev.typetype.server

import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationShortsDeduplicator
import dev.typetype.server.services.HomeRecommendationSourceTag
import dev.typetype.server.services.HomeRecommendationTaggedVideo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationShortsDeduplicatorTest {
    @Test
    fun `deduplicator removes history overlaps and semantic duplicates`() {
        val candidates = listOf(
            tagged(video("a", "funny cat jumps", "https://yt.com/c/c1")),
            tagged(video("b", "funny cat jumps compilation", "https://yt.com/c/c2")),
            tagged(video("c", "tech quick tip", "https://yt.com/c/c3")),
        )
        val result = HomeRecommendationShortsDeduplicator.apply(
            candidates = candidates,
            historyUrls = listOf("https://www.youtube.com/shorts/c"),
            subscriptionChannels = emptyList(),
        )
        assertEquals(1, result.size)
        assertTrue(result.first().video.url.endsWith("/a"))
    }

    private fun tagged(video: VideoItem): HomeRecommendationTaggedVideo =
        HomeRecommendationTaggedVideo(video = video, source = HomeRecommendationSourceTag.DISCOVERY_TRENDING)

    private fun video(id: String, title: String, channel: String): VideoItem = VideoItem(
        id = id,
        title = title,
        url = "https://www.youtube.com/shorts/$id",
        thumbnailUrl = "",
        uploaderName = channel,
        uploaderUrl = channel,
        uploaderAvatarUrl = "",
        duration = 30,
        viewCount = 0,
        uploadDate = "",
        uploaded = System.currentTimeMillis(),
        streamType = "video_stream",
        isShortFormContent = true,
        uploaderVerified = false,
        shortDescription = null,
    )
}
