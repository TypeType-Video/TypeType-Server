package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

class SabrLiveRoundedBoundaryWindowTest {
    @Test
    fun `live startup advances past rounded target boundary`() = runTest {
        val audio = format(140, true)
        val video = format(299, false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { state.isLive } returns true
        every { state.isPostLiveDvr } returns false
        every { state.liveHeadTimeMs } returns 4_594_000L
        every { state.liveHeadSequenceNumber } returns 4_594L
        val holder = holder(session, audio, video)
        holder.observeMediaSegment(mediaSegment(audio.itag, 4_573, 4_574_000L))
        holder.observeMediaSegment(mediaSegment(video.itag, 4_573, 4_574_000L))
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            request.takeIf { it.sequenceNumber == 4_573 }?.let { cached(it.format.itag) }
        }

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 4_573_966L, video.itag, audio.itag, bufferGoalMs = 1L),
        )

        assertTrue(result.isReady)
        assertTrue(result.blockedRequests.isEmpty())
        assertEquals(4_574_000L, result.response.startTimeMs)
        assertEquals(4_573, result.response.video?.segments?.single()?.url?.sequenceFromUrl())
        assertEquals(4_573, result.response.audio.segments.single().url.sequenceFromUrl())
    }

    private fun holder(
        session: YoutubeSabrSession,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
    ): SabrSessionHolder = SabrSessionHolder(
        session = session,
        info = mockk<YoutubeSabrInfo>(),
        audioFormat = audio,
        videoFormat = video,
        sessionToken = "session",
        key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat = mockk {
        every { this@mockk.itag } returns itag
        every { this@mockk.isAudio } returns isAudio
        every { mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        every { approxDurationMs } returns 4_594_000L
    }

    private fun mediaSegment(itag: Int, sequence: Int, startMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>(relaxed = true)
        every { header.itag } returns itag
        every { header.sequenceNumber } returns sequence
        every { header.startMs } returns startMs
        every { header.durationMs } returns 1_000L
        return mockk { every { this@mockk.header } returns header }
    }

    private fun cached(itag: Int): CachedSabrSegment = CachedSabrSegment(
        itag = itag,
        sequence = 4_573,
        init = false,
        startMs = 4_574_000L,
        durationMs = 1_000L,
        mimeType = if (itag == 140) "audio/mp4" else "video/mp4",
        bytesBase64 = "AA==",
        byteLength = 1,
    )

    private fun String.sequenceFromUrl(): Int = substringAfter("/segment/").substringBefore('?').toInt()
}
