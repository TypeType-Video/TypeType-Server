package dev.typetype.server

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationCursor
import dev.typetype.server.services.HomeRecommendationPage
import dev.typetype.server.services.HomeRecommendationShortsRefresher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationShortsRefresherTest {
    @Test
    fun `refresh rewinds discovery cursor when page has zero discovery`() {
        val pool = HomeRecommendationPool(
            subscriptions = (1..10).map { short("s$it") },
            discovery = (1..3).map { short("d$it") },
        )
        val cursor = HomeRecommendationCursor(subscriptionIndex = 10, discoveryIndex = 40)
        val page = HomeRecommendationPage(
            items = pool.subscriptions.take(10),
            nextCursor = null,
            subscriptionCount = 10,
            discoveryCount = 0,
            targetDiscoveryRatio = 0.6,
            discoveryFloorRatio = 0.65,
            sourceByUrl = emptyMap(),
        )
        val refreshed = HomeRecommendationShortsRefresher.refresh(pool, page, cursor)
        val override = refreshed.cursorOverride
        assertNotNull(override)
        assertEquals(16, override?.discoveryIndex)
        assertTrue(refreshed.pool.discovery.size > pool.discovery.size)
    }

    @Test
    fun `refresh rewinds cursor when page falls below floor`() {
        val pool = HomeRecommendationPool(
            subscriptions = (1..10).map { short("s$it") },
            discovery = (1..3).map { short("d$it") },
        )
        val cursor = HomeRecommendationCursor(subscriptionIndex = 10, discoveryIndex = 30)
        val page = HomeRecommendationPage(
            items = (pool.discovery.take(3) + pool.subscriptions.take(7)),
            nextCursor = null,
            subscriptionCount = 7,
            discoveryCount = 3,
            targetDiscoveryRatio = 0.6,
            discoveryFloorRatio = 0.65,
            sourceByUrl = emptyMap(),
        )
        val refreshed = HomeRecommendationShortsRefresher.refresh(pool, page, cursor)
        assertEquals(6, refreshed.cursorOverride?.discoveryIndex)
    }

    private fun short(id: String): VideoItem = VideoItem(
        id = id,
        title = id,
        url = "https://youtube.com/shorts/$id",
        thumbnailUrl = "",
        uploaderName = "channel-$id",
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
