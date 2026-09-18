package dev.typetype.server

import dev.typetype.server.services.HomeRecommendationLiveTitleDetector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeRecommendationLiveTitleDetectorTest {

    @Test
    fun `detects explicit live markers`() {
        assertEquals(true, HomeRecommendationLiveTitleDetector.isLiveLike("Channel is live now"))
        assertEquals(true, HomeRecommendationLiveTitleDetector.isLiveLike("DIRECT: football match"))
        assertEquals(true, HomeRecommendationLiveTitleDetector.isLiveLike("En direct ce soir"))
        assertEquals(true, HomeRecommendationLiveTitleDetector.isLiveLike("🔴 Livestream starting"))
    }

    @Test
    fun `detects sporting events with vs`() {
        assertEquals(true, HomeRecommendationLiveTitleDetector.isLiveLike("NRG vs B8 esports match"))
        assertEquals(true, HomeRecommendationLiveTitleDetector.isLiveLike("Japan vs Australia Full Match"))
    }

    @Test
    fun `detects multi-day events`() {
        assertEquals(true, HomeRecommendationLiveTitleDetector.isLiveLike("14 DAYS PRAYER"))
        assertEquals(true, HomeRecommendationLiveTitleDetector.isLiveLike("3 nights marathon"))
    }

    @Test
    fun `ignores normal content`() {
        assertEquals(false, HomeRecommendationLiveTitleDetector.isLiveLike("Weekly tech roundup"))
        assertEquals(false, HomeRecommendationLiveTitleDetector.isLiveLike("How to build a server"))
        assertEquals(false, HomeRecommendationLiveTitleDetector.isLiveLike("Top 10 games of 2023"))
        assertEquals(false, HomeRecommendationLiveTitleDetector.isLiveLike("This new Linux distro is breaking the law"))
        assertEquals(false, HomeRecommendationLiveTitleDetector.isLiveLike("Do All LTT Writers Think The Same"))
    }
}
