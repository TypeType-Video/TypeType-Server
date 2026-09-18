package dev.typetype.server

import dev.typetype.server.HomeRecommendationItemFixtures.context
import dev.typetype.server.HomeRecommendationItemFixtures.profile
import dev.typetype.server.HomeRecommendationItemFixtures.tagged
import dev.typetype.server.HomeRecommendationItemFixtures.video
import dev.typetype.server.services.HomeRecommendationPoolBuilder
import dev.typetype.server.services.HomeRecommendationSourceTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationPoolBuilderTest {
    @Test
    fun `pool builder excludes seen and blocked and dedups across sources`() {
        val profile = profile(
            seenUrls = setOf("https://yt.com/v/seen"),
            blockedVideos = setOf("https://yt.com/v/blocked"),
            blockedChannels = setOf("https://yt.com/c/blocked"),
            subscriptionChannels = setOf("https://yt.com/c/sub"),
            keywordAffinity = setOf("music"),
            personalizationEnabled = false,
        )
        val subscriptions = listOf(
            tagged(video("seen", "sub"), HomeRecommendationSourceTag.SUBSCRIPTION),
            tagged(video("blocked", "sub"), HomeRecommendationSourceTag.SUBSCRIPTION),
            tagged(video("ok", "sub", title = "music review"), HomeRecommendationSourceTag.SUBSCRIPTION),
            tagged(video("chblocked", "blocked"), HomeRecommendationSourceTag.SUBSCRIPTION),
        )
        val discovery = listOf(
            tagged(video("ok", "sub"), HomeRecommendationSourceTag.DISCOVERY_TRENDING),
            tagged(video("new", "x"), HomeRecommendationSourceTag.DISCOVERY_THEME),
        )
        val pool = HomeRecommendationPoolBuilder().build(profile, subscriptions, discovery, context)
        assertEquals(1, pool.subscriptions.size)
        assertEquals("https://yt.com/v/ok", pool.subscriptions.first().url)
        assertEquals(1, pool.discovery.size)
        assertEquals("https://yt.com/v/new", pool.discovery.first().url)
    }

    @Test
    fun `pool builder drops live-like discovery candidates`() {
        val profile = profile()
        val discovery = listOf(
            tagged(video("live1", "a", title = "Breaking is live now"), HomeRecommendationSourceTag.DISCOVERY_TRENDING),
            tagged(video("normal1", "b", title = "Weekly tech roundup"), HomeRecommendationSourceTag.DISCOVERY_THEME),
            tagged(video("live2", "c", title = "DIRECT: match day"), HomeRecommendationSourceTag.DISCOVERY_EXPLORATION),
        )
        val pool = HomeRecommendationPoolBuilder().build(profile, emptyList(), discovery, context)
        assertEquals(1, pool.discovery.size)
        assertTrue(pool.discovery.first().url.endsWith("/normal1"))
    }

    @Test
    fun `pool builder drops candidates marked live without relying on their title`() {
        val profile = profile()
        val discovery = listOf(
            tagged(
                video("live", "a", title = "Weekly tech roundup").copy(isLive = true),
                HomeRecommendationSourceTag.DISCOVERY_THEME,
            ),
            tagged(video("normal", "b", title = "Weekly tech roundup"), HomeRecommendationSourceTag.DISCOVERY_THEME),
        )

        val pool = HomeRecommendationPoolBuilder().build(profile, emptyList(), discovery, context)

        assertEquals(listOf("https://yt.com/v/normal"), pool.discovery.map { it.url })
    }

    @Test
    fun `pool builder excludes titles containing a blocked keyword`() {
        val profile = profile(blockedKeywords = setOf("ＳＰＯＮＳＯＲＥＤ"))
        val discovery = listOf(
            tagged(video("blocked", "a", title = "A sponsored review"), HomeRecommendationSourceTag.DISCOVERY_THEME),
            tagged(video("allowed", "b", title = "A regular review"), HomeRecommendationSourceTag.DISCOVERY_THEME),
        )

        val pool = HomeRecommendationPoolBuilder().build(profile, emptyList(), discovery, context)

        assertEquals(listOf("https://yt.com/v/allowed"), pool.discovery.map { it.url })
    }

    @Test
    fun `pool builder keeps neutral home source weights`() {
        val profile = profile(
            subscriptionEngagement = 6.0,
            discoveryEngagement = 1.0,
        )
        val pool = HomeRecommendationPoolBuilder().build(profile, emptyList(), emptyList(), context)
        assertTrue(pool.sourceWeights.isEmpty())
    }
}
