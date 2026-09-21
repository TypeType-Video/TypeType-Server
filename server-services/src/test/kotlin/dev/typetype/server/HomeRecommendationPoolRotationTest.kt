package dev.typetype.server

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.services.HomeRecommendationApiArgs
import dev.typetype.server.services.HomeRecommendationContext
import dev.typetype.server.services.HomeRecommendationCursor
import dev.typetype.server.services.HomeRecommendationCursorCodec
import dev.typetype.server.services.HomeRecommendationMixer
import dev.typetype.server.services.HomeRecommendationPageBuilder
import dev.typetype.server.services.HomeRecommendationPoolMode
import dev.typetype.server.services.HomeRecommendationPoolResolver
import dev.typetype.server.services.HomeRecommendationPoolRotation
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class HomeRecommendationPoolRotationTest {
    @Test
    fun `same seed keeps the pool order stable`() {
        val pool = pool()

        val first = HomeRecommendationPoolRotation.apply(pool, seed = 11L)
        val second = HomeRecommendationPoolRotation.apply(pool, seed = 11L)

        assertEquals(first, second)
    }

    @Test
    fun `different seeds rotate the candidate window`() {
        val pool = pool()

        val first = HomeRecommendationPoolRotation.apply(pool, seed = 11L)
        val second = HomeRecommendationPoolRotation.apply(pool, seed = 12L)

        assertNotEquals(first.subscriptions, second.subscriptions)
        assertNotEquals(first.discovery, second.discovery)
        assertEquals(pool.subscriptions.map { it.url }.toSet(), first.subscriptions.map { it.url }.toSet())
        assertEquals(pool.discovery.map { it.url }.toSet(), first.discovery.map { it.url }.toSet())
        assertEquals(pool.subscriptions.drop(40), first.subscriptions.drop(40))
        assertEquals(pool.discovery.drop(40), first.discovery.drop(40))
    }

    @Test
    fun `the rotation seed stays with the cursor across pages`() {
        val pool = HomeRecommendationPoolRotation.apply(pool(), seed = 11L)
        val first = HomeRecommendationMixer.mix(
            pool = pool,
            cursor = HomeRecommendationCursor(rotationSeed = 11L),
            limit = 20,
            context = HomeRecommendationItemFixtures.context,
        )
        val nextCursor = HomeRecommendationCursorCodec.decode(first.nextCursor)
        val second = HomeRecommendationMixer.mix(
            pool = pool,
            cursor = nextCursor ?: error("expected a next cursor"),
            limit = 20,
            context = HomeRecommendationItemFixtures.context,
        )

        assertEquals(11L, nextCursor.rotationSeed)
        assertEquals(emptySet<String>(), first.items.map { it.url }.toSet() intersect second.items.map { it.url }.toSet())
    }

    @Test
    fun `initial home pages receive a fresh seed while a seeded page is stable`() = runTest {
        val resolver = mockk<HomeRecommendationPoolResolver>()
        coEvery { resolver.resolve(any(), any(), any(), any()) } returns pool()
        val args = HomeRecommendationApiArgs(
            userId = "user",
            serviceId = 0,
            limit = 20,
            cursor = HomeRecommendationCursor(),
            context = HomeRecommendationContext(serviceId = 0, sessionContext = HomeRecommendationItemFixtures.context),
        )

        val first = HomeRecommendationPageBuilder.build(args, HomeRecommendationPoolMode.FULL, resolver)
        val second = HomeRecommendationPageBuilder.build(args, HomeRecommendationPoolMode.FULL, resolver)
        val firstCursor = HomeRecommendationCursorCodec.decode(first.nextCursor)

        assertNotEquals(first.items, second.items)
        assertNotEquals(0L, firstCursor?.rotationSeed)

        val seededArgs = args.copy(cursor = HomeRecommendationCursor(rotationSeed = 11L))
        val seededFirst = HomeRecommendationPageBuilder.build(seededArgs, HomeRecommendationPoolMode.FULL, resolver)
        val seededSecond = HomeRecommendationPageBuilder.build(seededArgs, HomeRecommendationPoolMode.FULL, resolver)
        assertEquals(seededFirst.items, seededSecond.items)
    }

    private fun pool(): HomeRecommendationPool = HomeRecommendationPool(
        subscriptions = (1..50).map { index -> HomeRecommendationItemFixtures.video("s$index", "s$index") },
        discovery = (1..50).map { index -> HomeRecommendationItemFixtures.video("d$index", "d$index") },
    )
}
