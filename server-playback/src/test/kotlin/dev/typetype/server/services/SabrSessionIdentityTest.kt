package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import java.time.Instant

class SabrSegmentDemandTrackerTest {
    @Test
    fun `known beyond end segment is not queued`() {
        withTracker { holder ->
            val request = SabrSegmentRequest.media(holder.audioFormat, 85)
            every { holder.session.isBeyondEnd(request) } returns true

            holder.requestSegmentDemand(request)

            assertNull(holder.pendingSegmentDemandSummary())
            assertNull(holder.segmentDemandIdentity(request))
            assertFalse(holder.playbackState() == SabrPlaybackState.TERMINAL)
        }
    }

    @Test
    fun `last known segment remains queued`() {
        withTracker { holder ->
            val request = SabrSegmentRequest.media(holder.audioFormat, 79)
            every { holder.session.isBeyondEnd(request) } answers { request.sequenceNumber > 79 }

            holder.requestSegmentDemand(request)

            assertEquals("140:79", holder.pendingSegmentDemandSummary())
        }
    }

    @Test
    fun `queued segment is removed when end becomes known`() {
        withTracker { holder ->
            val request = SabrSegmentRequest.media(holder.audioFormat, 85)
            var beyondEnd = false
            every { holder.session.isBeyondEnd(request) } answers { beyondEnd }

            holder.requestSegmentDemand(request)
            assertEquals("140:85", holder.pendingSegmentDemandSummary())

            beyondEnd = true

            assertNull(holder.pendingSegmentDemandSummary())
            assertNull(holder.segmentDemandIdentity(request))
        }
    }

    @Test
    fun `active live keeps a missing track demand behind the current head`() {
        withTracker { holder ->
            val request = SabrSegmentRequest.media(holder.videoFormat, 85)
            holder.markExpectedLive()
            every { holder.session.isBeyondEnd(request) } returns true

            holder.requestSegmentDemand(request)

            assertEquals("299:85", holder.pendingSegmentDemandSummary())
            assertEquals(request, holder.nextSegmentDemand())
        }
    }

    @Test
    fun `duplicate registration preserves exact demand identity`() {
        withTracker { holder ->
            val request = SabrSegmentRequest.media(holder.audioFormat, 42)
            every { holder.session.isBeyondEnd(request) } returns false

            holder.requestSegmentDemand(request, registeredAtMs = 100L)
            val identity = holder.segmentDemandIdentity(request)
            holder.requestSegmentDemand(request, registeredAtMs = 200L)

            assertEquals(identity, holder.segmentDemandIdentity(request))
            assertEquals(100L, identity?.let { holder.segmentDemandRegisteredAtMs(request, it) })
        }
    }

    private fun withTracker(block: (SabrSessionHolder) -> Unit): Unit {
        SabrSegmentDemandTracker.clearAll()
        try {
            block(holder())
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    private fun holder(): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val audio = format(140, true)
        val video = format(299, false)
        every { session.getCachedSegment(any()) } returns null
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
        )
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        return format
    }
}
