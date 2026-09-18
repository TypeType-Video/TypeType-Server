package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrBufferedRange
import dev.typetype.server.sabr.SabrMediaHeader
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrLiveSessionWarmupTest {
    @Test
    fun `warmup continues when the first media pair is too close to the live head`() = runTest {
        val audio = format(140, audio = true, "audio/mp4")
        val video = format(299, audio = false, "video/mp4")
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        val session = mockk<YoutubeSabrSession>()
        var pumps = 0
        every { session.streamState } returns streamState
        every { session.isComplete } returns false
        every { session.isLive } returns true
        every { session.liveHeadSequenceNumber } returns 1_000L
        every { streamState.isLive } returns true
        every { streamState.isPostLiveDvr } returns false
        every { streamState.liveHeadSequenceNumber } returns 1_000L
        every { streamState.liveHeadTimeMs } returns 2_000_000L
        val audioInit = mp4Box("ftyp", byteArrayOf(1)) + mp4Box("moov", byteArrayOf(2))
        val videoInit = mp4Box("ftyp", byteArrayOf(3)) + mp4Box("moov", byteArrayOf(4))
        every { session.pumpOnce(any()) } answers {
            pumps++
            val atTarget = pumps > 1
            val sequence = if (atTarget) 990 else 1_000
            val startMs = if (atTarget) 1_980_000L else 2_000_000L
            listOf(
                segment(140, sequence, startMs, 2_000L, audioInit + mediaFragment(5)),
                segment(299, sequence, startMs, 2_000L, videoInit + mediaFragment(6)),
            )
        }
        val holder = holder(session, audio, video)
        holder.markExpectedLive()

        SabrSessionPump(SabrSegmentCache()).ensureWarmed(holder, maxPumps = 8)

        assertEquals(2, pumps)
        assertEquals(1_980_000L, holder.earliestObservedMediaStartMs(audio))
        assertEquals(1_980_000L, holder.earliestObservedMediaStartMs(video))
        assertEquals(1_980_000L, holder.resolvePlaybackStartMs(0L))
    }

    @Test
    fun `warmup keeps bootstrap initialization and requests real live media`() = runTest {
        val audio = format(140, audio = true, "audio/mp4")
        val video = format(299, audio = false, "video/mp4")
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        val session = mockk<YoutubeSabrSession>()
        val rangeOverrides = mutableListOf<List<SabrBufferedRange>?>()
        var pumps = 0
        every { session.streamState } returns streamState
        every { session.isComplete } returns false
        every { session.isLive } answers { pumps > 0 }
        every { session.isAtLiveEdge } returns false
        every { session.liveHeadSequenceNumber } answers { if (pumps > 0) liveHeadSequence(pumps) else -1L }
        every { streamState.isPostLiveDvr } returns false
        every { streamState.isLive } answers { pumps > 0 }
        every { streamState.liveHeadSequenceNumber } answers { if (pumps > 0) liveHeadSequence(pumps) else -1L }
        every { streamState.liveHeadTimeMs } answers { if (pumps > 0) liveHeadTimeMs(pumps) else -1L }
        every { streamState.getBufferedEndMs(audio) } returns 0L
        every { streamState.getBufferedEndMs(video) } returns 0L
        every { streamState.getMinBufferedEndMs() } returns 0L
        every { streamState.setBufferedRangesOverride(any()) } answers {
            rangeOverrides += firstArg<List<SabrBufferedRange>?>()
        }
        val audioInit = mp4Box("ftyp", byteArrayOf(1)) + mp4Box("moov", byteArrayOf(2))
        val videoInit = mp4Box("ftyp", byteArrayOf(3)) + mp4Box("moov", byteArrayOf(4))
        val bootstrap = listOf(
            segment(140, 0, 0L, -1L, audioInit + mediaFragment(5)),
            segment(299, 0, 0L, -1L, videoInit + mediaFragment(6)),
        )
        every { session.pumpOnce(any()) } answers {
            pumps++
            when (pumps) {
                1 -> bootstrap
                2 -> emptyList()
                else -> {
                    val offset = pumps - 3
                    listOf(
                        segment(
                            140,
                            TARGET_SEQUENCE + offset,
                            TARGET_TIME_MS + offset * 2_000L,
                            2_000L,
                            audioInit + mediaFragment(7),
                        ),
                        segment(
                            299,
                            TARGET_SEQUENCE + offset,
                            TARGET_TIME_MS + offset * 2_000L,
                            2_000L,
                            videoInit + mediaFragment(8),
                        ),
                    )
                }
            }
        }
        val holder = holder(session, audio, video)
        holder.markExpectedLive()

        SabrSessionPump(SabrSegmentCache()).ensureWarmed(holder, maxPumps = 8)

        assertEquals(3, pumps)
        assertEquals(TARGET_SEQUENCE, holder.observedMediaSegment(audio)?.header?.sequenceNumber)
        assertEquals(TARGET_SEQUENCE, holder.observedMediaSegment(video)?.header?.sequenceNumber)
        assertArrayEquals(audioInit, holder.liveInitialization(audio))
        assertArrayEquals(videoInit, holder.liveInitialization(video))
        assertEquals(liveHeadTimeMs(pumps) - 20_000L, holder.resolvePlaybackStartMs(0L))
        val targetedRanges = rangeOverrides.filterNotNull().map { ranges -> ranges.map(SabrBufferedRange::summarize) }
        val expectedRanges = listOf(
            "itag=140:seq=1-3319:time=0+6640000:timescale=1000",
            "itag=299:seq=1-3319:time=0+6640000:timescale=1000",
        )
        assertEquals(2, targetedRanges.size)
        assertEquals(expectedRanges, targetedRanges.first())
        assertEquals(expectedRanges, targetedRanges[1])
        assertNull(rangeOverrides.last())
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
        sessionToken = "session-token",
        key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, audio: Boolean, mimeType: String): YoutubeSabrFormat =
        mockk<YoutubeSabrFormat>(relaxed = true) {
            every { this@mockk.itag } returns itag
            every { isAudio } returns audio
            every { isVideo } returns !audio
            every { this@mockk.mimeType } returns mimeType
            every { lastModified } returns 1L
        }

    private fun segment(
        itag: Int,
        sequence: Int,
        startMs: Long,
        durationMs: Long,
        data: ByteArray,
    ): SabrMediaSegment {
        val header = mockk<SabrMediaHeader> {
            every { isInitSegment } returns false
            every { this@mockk.itag } returns itag
            every { sequenceNumber } returns sequence
            every { this@mockk.startMs } returns startMs
            every { this@mockk.durationMs } returns durationMs
        }
        return mockk {
            every { this@mockk.header } returns header
            every { this@mockk.data } returns data
        }
    }

    private fun mediaFragment(value: Byte): ByteArray =
        mp4Box("moof", byteArrayOf(value)) + mp4Box("mdat", byteArrayOf(value))

    private fun liveHeadSequence(pumps: Int): Long = LIVE_HEAD_SEQUENCE + (pumps - 1) * 3L

    private fun liveHeadTimeMs(pumps: Int): Long = LIVE_HEAD_TIME_MS + (pumps - 1) * 6_000L

    private fun mp4Box(type: String, payload: ByteArray): ByteArray {
        val size = payload.size + 8
        return byteArrayOf(0, 0, 0, size.toByte()) + type.toByteArray(Charsets.US_ASCII) + payload
    }

    private companion object {
        const val LIVE_HEAD_SEQUENCE = 3_330L
        const val LIVE_HEAD_TIME_MS = 6_660_000L
        const val TARGET_SEQUENCE = 3_320
        const val TARGET_TIME_MS = 6_640_000L
    }
}
