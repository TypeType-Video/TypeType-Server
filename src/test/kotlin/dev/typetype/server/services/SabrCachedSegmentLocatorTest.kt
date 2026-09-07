package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrMediaHeader
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrSession

class SabrCachedSegmentLocatorTest {
    @Test
    fun `finds authoritative cached segment when predicted sequence is stale`() {
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>()
        val segment = segment(sequence = 101, startMs = 488_200L, durationMs = 6_500L)
        every { format.itag } returns 299
        every { session.getCachedSegment(any()) } answers {
            firstArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 101 }?.let { segment }
        }

        val found = session.findCachedMediaAt(
            format = format,
            targetMs = 491_203L,
            predictedSequence = 98,
        )

        assertSame(segment, found)
    }

    @Test
    fun `finds next cached sequence across millisecond timing gap`() {
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>()
        val segment = segment(sequence = 41, startMs = 202_302L, durationMs = 5_672L)
        every { format.itag } returns 315
        every { session.getCachedSegment(any()) } answers {
            firstArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 41 }?.let { segment }
        }

        val found = session.findCachedMediaAt(
            format = format,
            targetMs = 202_301L,
            predictedSequence = 40,
        )

        assertSame(segment, found)
    }

    @Test
    fun `uses derived live duration when media header omits duration`() {
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>()
        val segment = segment(sequence = 2_049_677, startMs = 4_099_349_989L, durationMs = -1L)
        every { format.itag } returns 140
        every { session.getCachedSegment(any()) } answers {
            firstArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 2_049_677 }?.let { segment }
        }

        val found = session.findCachedMediaAt(
            format = format,
            targetMs = 4_099_347_988L,
            predictedSequence = 2_049_676,
            fallbackDurationMs = 2_001L,
            allowFollowing = true,
        )

        assertSame(segment, found)
    }

    @Test
    fun `finds next live segment across a multi-sequence gap`() {
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>()
        val segment = segment(sequence = 3_909, startMs = 19_545_066L, durationMs = 5_000L)
        every { format.itag } returns 137
        every { session.getCachedSegment(any()) } answers {
            firstArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 3_909 }?.let { segment }
        }

        val found = session.findCachedMediaAt(
            format = format,
            targetMs = 19_530_066L,
            predictedSequence = 3_906,
            fallbackDurationMs = 5_000L,
            allowFollowing = true,
        )

        assertSame(segment, found)
    }

    @Test
    fun `accepts cumulative audio rounding across a live sequence gap`() {
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>()
        val segment = segment(sequence = 3_980, startMs = 19_900_081L, durationMs = -1L)
        every { format.itag } returns 140
        every { session.getCachedSegment(any()) } answers {
            firstArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 3_980 }?.let { segment }
        }

        val found = session.findCachedMediaAt(
            format = format,
            targetMs = 19_865_073L,
            predictedSequence = 3_973,
            fallbackDurationMs = 5_000L,
            allowFollowing = true,
        )

        assertSame(segment, found)
    }

    @Test
    fun `accepts live segment just beyond exact sequence duration`() {
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>()
        val segment = segment(sequence = 5_947, startMs = 29_735_079L, durationMs = -1L)
        every { format.itag } returns 140
        every { session.getCachedSegment(any()) } answers {
            firstArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 5_947 }?.let { segment }
        }

        val found = session.findCachedMediaAt(
            format = format,
            targetMs = 29_720_066L,
            predictedSequence = 5_944,
            fallbackDurationMs = 5_000L,
            allowFollowing = true,
        )

        assertSame(segment, found)
    }

    @Test
    fun `rejects following live segment beyond its sequence range`() {
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>()
        val segment = segment(sequence = 3_909, startMs = 19_550_067L, durationMs = 5_000L)
        every { format.itag } returns 137
        every { session.getCachedSegment(any()) } answers {
            firstArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 3_909 }?.let { segment }
        }

        val found = session.findCachedMediaAt(
            format = format,
            targetMs = 19_530_066L,
            predictedSequence = 3_906,
            fallbackDurationMs = 5_000L,
            allowFollowing = true,
        )

        assertNull(found)
    }

    private fun segment(sequence: Int, startMs: Long, durationMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.sequenceNumber } returns sequence
        every { header.isInitSegment } returns false
        every { header.startMs } returns startMs
        every { header.durationMs } returns durationMs
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        return segment
    }
}
