package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrMediaHeader
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrSegmentDemandResolutionTest {
    @AfterEach
    fun clearDemands(): Unit = SabrSegmentDemandTracker.clearAll()

    @Test
    fun `resolves skipped live sequence against requested segment time`() {
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        val request = SabrSegmentRequest.media(format, 1_904)
        val replacement = segment(sequence = 1_905, startMs = 9_520_445L, durationMs = 5_000L)
        every { format.itag } returns 140
        every { format.isAudio } returns true
        every { session.streamState } returns state
        every { state.getSegmentStartMs(format, 1_904) } returns 9_520_445L
        every { session.getCachedSegment(any()) } answers {
            firstArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 1_905 }?.let { replacement }
        }
        val holder = holder(session, format)
        holder.requestSegmentDemand(request)
        val identity = requireNotNull(holder.segmentDemandIdentity(request))

        assertTrue(holder.resolveSegmentDemand(request, identity))

        assertNull(holder.pendingSegmentDemandSummary())
        assertSame(replacement, holder.observedMediaSegment(format))
        verify(exactly = 1) { state.jumpBufferedTo(format, 1_905) }
    }

    @Test
    fun `resolves live sequence gap from the next available segment`() {
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        val request = SabrSegmentRequest.media(format, 92)
        val replacement = segment(itag = 299, sequence = 93, startMs = 480_000L, durationMs = -1L)
        every { format.itag } returns 299
        every { format.isAudio } returns false
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { state.isLive } returns true
        every { state.getSegmentStartMs(format, 92) } returns 475_000L
        every { state.getSegmentEndMs(format, 92) } returns 480_000L
        every { session.getCachedSegment(any()) } answers {
            firstArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 93 }?.let { replacement }
        }
        val holder = holder(session, format)
        holder.requestSegmentDemand(request)
        val identity = requireNotNull(holder.segmentDemandIdentity(request))

        assertTrue(holder.resolveSegmentDemand(request, identity))

        assertNull(holder.pendingSegmentDemandSummary())
        assertSame(replacement, holder.observedMediaSegment(format))
        verify(exactly = 1) { state.jumpBufferedTo(format, 93) }
    }

    @Test
    fun `resolved requested segment advances complete media anchor`() {
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val request = SabrSegmentRequest.media(format, 2_752)
        val cached = segment(sequence = 2_752, startMs = 13_757_066L, durationMs = 4_997L)
        var cacheReady = false
        every { format.itag } returns 140
        every { format.isAudio } returns true
        every { session.getCachedSegment(request) } answers { cached.takeIf { cacheReady } }
        val holder = holder(session, format)
        holder.requestSegmentDemand(request)
        val identity = requireNotNull(holder.segmentDemandIdentity(request))
        cacheReady = true

        assertTrue(holder.resolveSegmentDemand(request, identity))

        assertSame(cached, holder.observedMediaSegment(format))
        assertNull(holder.pendingSegmentDemandSummary())
    }

    @Test
    fun `resolved demand remains in server cache after extractor eviction`() {
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val request = SabrSegmentRequest.media(format, 2_752)
        val cached = segment(sequence = 2_752, startMs = 13_757_066L, durationMs = 4_997L)
        var extractorCached = false
        every { format.itag } returns 140
        every { format.isAudio } returns true
        every { format.mimeType } returns "audio/mp4"
        every { format.lastModified } returns 0L
        every { format.xtags } returns ""
        every { cached.data } returns byteArrayOf(1, 2, 3)
        every { session.getCachedSegment(request) } answers { cached.takeIf { extractorCached } }
        val holder = holder(session, format)
        val serverCache = SabrSegmentCache()
        holder.requestSegmentDemand(request)
        val identity = requireNotNull(holder.segmentDemandIdentity(request))
        extractorCached = true

        assertTrue(holder.resolveSegmentDemand(request, identity) { serverCache.put(holder, it) })
        extractorCached = false

        assertArrayEquals(byteArrayOf(1, 2, 3), serverCache.get(holder, request)?.bytes)
    }

    private fun holder(session: YoutubeSabrSession, audio: YoutubeSabrFormat): SabrSessionHolder {
        val video = mockk<YoutubeSabrFormat>()
        every { video.itag } returns 299
        every { video.isAudio } returns false
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

    private fun segment(sequence: Int, startMs: Long, durationMs: Long, itag: Int = 140): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.sequenceNumber } returns sequence
        every { header.startMs } returns startMs
        every { header.durationMs } returns durationMs
        every { header.itag } returns itag
        every { header.isInitSegment } returns false
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        return segment
    }
}
