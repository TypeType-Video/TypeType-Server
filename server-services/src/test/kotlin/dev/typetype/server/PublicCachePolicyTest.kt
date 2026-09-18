package dev.typetype.server

import dev.typetype.server.services.BILIBILI_SERVICE_ID
import dev.typetype.server.services.PublicCacheKey
import dev.typetype.server.services.PublicCachePolicy
import dev.typetype.server.services.YOUTUBE_SERVICE_ID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublicCachePolicyTest {

    @Test
    fun `public cache keys are versioned and hashed`() {
        val key = PublicCacheKey.of("search", YOUTUBE_SERVICE_ID.toString(), "rick", "cursor-token")
        assertTrue(key.startsWith("search:v2:"))
        assertFalse(key.contains("rick"))
        assertFalse(key.contains("cursor-token"))
    }

    @Test
    fun `trending ttl depends on service volatility`() {
        assertEquals(1_800L, PublicCachePolicy.trendingTtl(YOUTUBE_SERVICE_ID))
        assertEquals(600L, PublicCachePolicy.trendingTtl(BILIBILI_SERVICE_ID))
    }

    @Test
    fun `search ttl is shorter for cursored pages`() {
        assertEquals(600L, PublicCachePolicy.searchTtl(YOUTUBE_SERVICE_ID, null))
        assertEquals(300L, PublicCachePolicy.searchTtl(YOUTUBE_SERVICE_ID, "cursor"))
    }

    @Test
    fun `channel ttl is shorter for channel search and volatile sorts`() {
        assertEquals(3_600L, PublicCachePolicy.channelTtl("https://www.youtube.com/channel/id", null, null))
        assertEquals(
            600L,
            PublicCachePolicy.channelTtl("https://www.youtube.com/channel/id/search?query=x", null, null),
        )
        assertEquals(900L, PublicCachePolicy.channelTtl("https://www.youtube.com/channel/id", null, "latest"))
    }

    @Test
    fun `channel ttl keeps livestream state fresh`() {
        assertEquals(60L, PublicCachePolicy.channelTtl("https://www.youtube.com/channel/id/streams", null, null))
        assertEquals(60L, PublicCachePolicy.channelTtl("https://www.youtube.com/channel/id/livestreams", null, null))
        assertEquals(300L, PublicCachePolicy.channelTtl("https://www.youtube.com/channel/id/streams", "cursor", null))
    }

    @Test
    fun `comments ttl is shortest on first youtube page`() {
        assertEquals(180L, PublicCachePolicy.commentsTtl("https://youtube.com/watch?v=id", null))
        assertEquals(600L, PublicCachePolicy.commentsTtl("https://youtube.com/watch?v=id", "cursor"))
        assertEquals(300L, PublicCachePolicy.commentsTtl("https://www.bilibili.com/video/id", null))
    }
}
