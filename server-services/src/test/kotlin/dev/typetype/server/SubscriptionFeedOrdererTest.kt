package dev.typetype.server

import dev.typetype.server.SubscriptionFeedTestFixtures.video
import dev.typetype.server.services.SubscriptionFeedOrderer
import dev.typetype.server.services.SubscriptionFeedSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionFeedOrdererTest {
    private val orderer = SubscriptionFeedOrderer()

    @Test
    fun `scheduled livestream is promoted once then newer uploads pass it`() {
        val discoveredAt = 1_800_000_000_000L
        val scheduled = video(discoveredAt + 86_400_000L, url = "scheduled").copy(duration = 0L)
        val first = orderer.order(
            listOf(scheduled, video(discoveredAt - 1_000L, url = "existing")),
            previous = null,
            refreshedAt = discoveredAt,
        )
        assertEquals(listOf("scheduled", "existing"), first.videos.map { it.url })
        val previous = snapshot(discoveredAt, first.videos, first.livePromotedAt)

        val result = orderer.order(
            listOf(scheduled, video(discoveredAt + 1_000L, url = "recent")),
            previous,
            refreshedAt = discoveredAt + 2_000L,
        )

        assertEquals(listOf("recent", "scheduled"), result.videos.map { it.url })
        assertEquals(discoveredAt, result.livePromotedAt["scheduled"])
    }

    @Test
    fun `scheduled to live transition is promoted once`() {
        val scheduled = video(20_000L, url = "live").copy(duration = 0L)
        val previous = snapshot(5_000L, listOf(scheduled), mapOf("live" to 5_000L))
        val live = video(-1L, url = "live", live = true)

        val promoted = orderer.order(listOf(video(8_000L), live), previous, refreshedAt = 10_000L)

        assertEquals("live", promoted.videos.first().url)
        assertEquals(10_000L, promoted.livePromotedAt["live"])
    }

    @Test
    fun `active livestream keeps its promotion while newer uploads pass it`() {
        val live = video(-1L, url = "live", live = true)
        val previous = snapshot(
            generatedAt = 10_000L,
            videos = listOf(live),
            livePromotedAt = mapOf("live" to 10_000L),
        )

        val result = orderer.order(listOf(live, video(12_000L, url = "new")), previous, refreshedAt = 20_000L)

        assertEquals(listOf("new", "live"), result.videos.map { it.url })
        assertEquals(10_000L, result.livePromotedAt["live"])
    }

    @Test
    fun `old snapshot gives an existing live one migration promotion`() {
        val live = video(-1L, url = "live", live = true)
        val previous = snapshot(7_000L, listOf(live))

        val result = orderer.order(listOf(live), previous, refreshedAt = 20_000L)

        assertEquals(7_000L, result.livePromotedAt["live"])
    }

    private fun snapshot(
        generatedAt: Long,
        videos: List<dev.typetype.server.models.VideoItem>,
        livePromotedAt: Map<String, Long> = emptyMap(),
    ) = SubscriptionFeedSnapshot(
        generation = 1L,
        generatedAt = generatedAt,
        stale = false,
        videos = videos,
        livePromotedAt = livePromotedAt,
    )
}
