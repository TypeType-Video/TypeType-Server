package dev.typetype.server

import dev.typetype.server.services.HomeRecommendationCursor
import dev.typetype.server.services.HomeRecommendationCursorCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationCursorCodecTest {

    @Test
    fun `decode null cursor defaults to zero indexes`() {
        val cursor = HomeRecommendationCursorCodec.decode(null)
        assertEquals(0, cursor?.subscriptionIndex)
        assertEquals(0, cursor?.discoveryIndex)
    }

    @Test
    fun `encode decode roundtrip preserves indexes`() {
        val encoded = HomeRecommendationCursorCodec.encode(
            HomeRecommendationCursor(
                subscriptionIndex = 40,
                discoveryIndex = 12,
                subscriptionRun = 2,
                preferDiscovery = false,
                rotationSeed = 123456789L,
                recentChannels = listOf("c1", "c2"),
                recentSemanticKeys = listOf("linux|kernel"),
                creatorMomentum = mapOf("https://yt.com/c/a" to 2),
                creatorCooldownUntilMs = mapOf("https://yt.com/c/a" to 123L),
                recentTopicPairs = listOf("linux|kernel"),
                recentUrls = listOf("https://yt.com/v/a"),
            )
        )
        val decoded = HomeRecommendationCursorCodec.decode(encoded)
        assertTrue(encoded.startsWith("z."))
        assertEquals(40, decoded?.subscriptionIndex)
        assertEquals(12, decoded?.discoveryIndex)
        assertEquals(2, decoded?.subscriptionRun)
        assertEquals(false, decoded?.preferDiscovery)
        assertEquals(123456789L, decoded?.rotationSeed)
        assertEquals(listOf("c1", "c2"), decoded?.recentChannels)
        assertEquals(listOf("linux|kernel"), decoded?.recentSemanticKeys)
        assertEquals(2, decoded?.creatorMomentum?.get("https://yt.com/c/a"))
        assertEquals(123L, decoded?.creatorCooldownUntilMs?.get("https://yt.com/c/a"))
        assertEquals(listOf("linux|kernel"), decoded?.recentTopicPairs)
        assertEquals(listOf("https://yt.com/v/a"), decoded?.recentUrls)
    }

    @Test
    fun `decode legacy index cursor maps to both indexes`() {
        val legacy = "eyJpbmRleCI6NX0"
        val decoded = HomeRecommendationCursorCodec.decode(legacy)
        assertEquals(5, decoded?.subscriptionIndex)
        assertEquals(5, decoded?.discoveryIndex)
    }

    @Test
    fun `decode invalid cursor returns null`() {
        assertNull(HomeRecommendationCursorCodec.decode("not_base64"))
    }

    @Test
    fun `large cursor stays below practical request limits`() {
        val cursor = HomeRecommendationCursor(
            recentUrls = (1..80).map { "https://www.youtube.com/watch?v=${it.toString().padStart(11, '0')}" },
            creatorMomentum = (1..40).associate { "https://www.youtube.com/channel/channel-$it" to it },
        )
        val encoded = HomeRecommendationCursorCodec.encode(cursor)
        assertTrue(encoded.length < 2_048)
        assertEquals(cursor, HomeRecommendationCursorCodec.decode(encoded))
    }
}
