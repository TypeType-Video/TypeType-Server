package dev.typetype.server

import dev.typetype.server.services.HlsSegmentChain
import dev.typetype.server.services.HlsSegmentChain.Segment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HlsSegmentChainTest {
    private fun timeline(prefix: String, count: Int, stepMs: Long) = (0 until count).map { Segment("$prefix$it", it * stepMs) }

    @Test
    fun `a video segment leads to the next one and to the audio playing beside it`() {
        val chain = HlsSegmentChain()
        chain.remember(listOf(timeline("high", 4, 6_000), timeline("low", 4, 6_000)), timeline("audio", 6, 4_800))

        assertEquals(listOf("high3", "audio2"), chain.following("high2"))
        assertEquals(listOf("low1", "audio0"), chain.following("low0"))
        assertEquals(listOf("audio3"), chain.following("high3"))
    }

    @Test
    fun `an audio segment only leads to the next audio segment`() {
        val chain = HlsSegmentChain()
        chain.remember(listOf(timeline("video", 2, 6_000)), timeline("audio", 2, 6_000))

        assertEquals(listOf("audio1"), chain.following("audio0"))
        assertEquals(emptyList<String>(), chain.following("audio1"))
        assertEquals(emptyList<String>(), chain.following("unknown"))
    }

    @Test
    fun `forgets the oldest videos past its size`() {
        val chain = HlsSegmentChain(maxSegments = 6)
        chain.remember(listOf(timeline("a", 3, 6_000)), null)
        chain.remember(listOf(timeline("b", 3, 6_000)), null)
        chain.remember(listOf(timeline("a", 3, 6_000)), null)
        chain.remember(listOf(timeline("c", 3, 6_000)), null)

        assertEquals(listOf("a1"), chain.following("a0"))
        assertEquals(emptyList<String>(), chain.following("b0"))
        assertEquals(listOf("c1"), chain.following("c0"))
    }
}
