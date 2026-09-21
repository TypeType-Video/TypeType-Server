package dev.typetype.server

import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationCandidatePool
import dev.typetype.server.services.HomeRecommendationShortsFallback
import dev.typetype.server.services.HomeRecommendationSourceTag
import dev.typetype.server.services.HomeRecommendationTaggedVideo
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationShortsFallbackTest {
    @Test
    fun `apply injects fallback discovery when discovery empty`() {
        val pool = HomeRecommendationCandidatePool(
            subscriptions = (1..5).map { index -> tagged("s$index", HomeRecommendationSourceTag.SUBSCRIPTION) },
            discovery = emptyList(),
        )
        val result = HomeRecommendationShortsFallback.apply(pool)
        assertTrue(result.discovery.isNotEmpty())
        assertTrue(result.discovery.all { it.source == HomeRecommendationSourceTag.DISCOVERY_EXPLORATION })
    }

    private fun tagged(id: String, source: HomeRecommendationSourceTag): HomeRecommendationTaggedVideo =
        HomeRecommendationTaggedVideo(video(id), source)

    private fun video(id: String): VideoItem = VideoItem(
        id = id,
        title = id,
        url = "https://youtube.com/shorts/$id",
        thumbnailUrl = "",
        uploaderName = "Channel$id",
        uploaderUrl = "https://yt.com/c/$id",
        uploaderAvatarUrl = "",
        duration = 30,
        viewCount = 0,
        uploadDate = "",
        uploaded = 0,
        streamType = "video_stream",
        isShortFormContent = true,
        uploaderVerified = false,
        shortDescription = null,
    )
}
