package dev.typetype.server

import dev.typetype.server.services.HomeRecommendationTemporalBoost
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class HomeRecommendationTemporalBoostTest {
    @Test
    fun `morning keywords receive boost`() {
        val score = HomeRecommendationTemporalBoost.boost(
            title = "daily news brief",
            now = LocalDateTime.of(2026, 4, 6, 8, 0),
        )
        assertTrue(score > 0.0)
    }

    @Test
    fun `evening keywords receive boost`() {
        val score = HomeRecommendationTemporalBoost.boost(
            title = "music chill mix",
            now = LocalDateTime.of(2026, 4, 6, 20, 0),
        )
        assertTrue(score > 0.0)
    }
}
