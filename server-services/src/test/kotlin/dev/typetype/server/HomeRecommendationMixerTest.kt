package dev.typetype.server

import dev.typetype.server.HomeRecommendationItemFixtures.context
import dev.typetype.server.HomeRecommendationItemFixtures.video
import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.services.HomeRecommendationCursor
import dev.typetype.server.services.HomeRecommendationMixer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationMixerTest {
    @Test
    fun `mix enforces no consecutive same channel and max two per channel`() {
        val pool = HomeRecommendationPool(
            subscriptions = listOf(
                video("s1", "a"),
                video("s2", "a"),
                video("s3", "a"),
                video("s4", "b"),
                video("s5", "c"),
            ),
            discovery = listOf(
                video("d1", "a"),
                video("d2", "b"),
                video("d3", "d"),
                video("d4", "e"),
            ),
        )
        val page = HomeRecommendationMixer.mix(
            pool = pool,
            cursor = HomeRecommendationCursor(subscriptionIndex = 0, discoveryIndex = 0),
            limit = 8,
            context = context,
        )
        val items = page.items
        val channels = items.map { it.uploaderUrl }
        for (i in 1 until channels.size) {
            assertTrue(channels[i] != channels[i - 1])
        }
        channels.groupingBy { it }.eachCount().values.forEach { count ->
            assertTrue(count <= 2)
        }
    }

    @Test
    fun `mix emits next cursor when more items remain`() {
        val pool = HomeRecommendationPool(
            subscriptions = listOf(video("s1", "a"), video("s2", "b"), video("s3", "c")),
            discovery = listOf(video("d1", "d"), video("d2", "e"), video("d3", "f")),
        )
        val page = HomeRecommendationMixer.mix(
            pool = pool,
            cursor = HomeRecommendationCursor(subscriptionIndex = 0, discoveryIndex = 0),
            limit = 3,
            context = context,
        )
        assertEquals(3, page.items.size)
        assertNotNull(page.nextCursor)
    }

    @Test
    fun `mix enforces at least half discovery when stock exists`() {
        val subs = (1..12).map { index -> video("s$index", "s$index") }
        val discover = (1..12).map { index -> video("d$index", "d$index") }
        val pool = HomeRecommendationPool(subscriptions = subs, discovery = discover)
        val page = HomeRecommendationMixer.mix(
            pool = pool,
            cursor = HomeRecommendationCursor(),
            limit = 10,
            context = context,
        )
        assertTrue(page.discoveryCount >= 5)
        assertTrue(page.subscriptionCount <= 5)
    }

    @Test
    fun `mix prevents more than two subscription picks in a row`() {
        val subs = (1..12).map { index -> video("s$index", "s$index") }
        val discover = (1..12).map { index -> video("d$index", "d$index") }
        val pool = HomeRecommendationPool(subscriptions = subs, discovery = discover)
        val page = HomeRecommendationMixer.mix(
            pool = pool,
            cursor = HomeRecommendationCursor(),
            limit = 10,
            context = context,
        )
        val subUrls = pool.subscriptions.map { it.url }.toSet()
        var run = 0
        page.items.forEach { item ->
            if (item.url in subUrls) {
                run += 1
                assertTrue(run <= 2)
            } else {
                run = 0
            }
        }
    }
}
