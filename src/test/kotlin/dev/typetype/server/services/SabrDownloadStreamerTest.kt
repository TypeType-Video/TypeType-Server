package dev.typetype.server.services

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class SabrDownloadStreamerTest {
    @Test
    fun `streams cached track in order and discards each segment`() = runTest {
        val store = mockk<SabrSessionStore>()
        val holder = mockk<SabrSessionHolder>()
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        val audio = format(140)
        val media = segment(byteArrayOf(3, 4, 5))
        val cached = mutableMapOf(1 to media)
        every { holder.session } returns session
        every { holder.audioFormat } returns audio
        every { holder.isAudioActive() } returns true
        every { holder.isVideoActive() } returns false
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 137, 0, SabrSessionPurpose.DOWNLOAD)
        every { holder.playerContextToken } returns null
        every { store.initCache } returns null
        every { session.streamState } returns state
        every { state.poToken } returns null
        every { state.getSegmentNumberAtOrAfterTimeMs(audio, 0) } returns 1
        every { state.isComplete(audio) } answers { cached.isEmpty() }
        every { session.isBeyondEnd(any()) } returns false
        every { session.cachedBytes } returns 0
        every { session.totalResponseBytes } returns 3
        every { session.maxResponseBytes } returns 3
        every { session.getCachedSegment(any()) } answers {
            val request = firstArg<SabrSegmentRequest>()
            cached[request.sequenceNumber]
        }
        every { session.discardCachedSegment(any()) } answers {
            cached.remove(firstArg<SabrSegmentRequest>().sequenceNumber)
        }
        coEvery { store.fetchInitializationData(holder, audio) } returns byteArrayOf(1, 2)
        val output = ByteArrayOutputStream()

        SabrDownloadStreamer(store).stream(holder, output)

        val expected = ByteArrayOutputStream().also {
            val writer = SabrDownloadFrameWriter(it)
            writer.start()
            writer.initialization(140, byteArrayOf(1, 2))
            writer.media(140, 1, 3, ByteArrayInputStream(byteArrayOf(3, 4, 5)))
            writer.finish()
        }.toByteArray()
        assertArrayEquals(expected, output.toByteArray())
        verify { session.discardCachedSegment(match { it.sequenceNumber == 1 }) }
    }

    @Test
    fun `discards cached media before multipart boundary`() = runTest {
        val store = mockk<SabrSessionStore>()
        val holder = mockk<SabrSessionHolder>()
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        val audio = format(140)
        val stale = segment(byteArrayOf(9))
        val current = segment(byteArrayOf(3, 4))
        val cached = mutableMapOf(1 to stale, 5 to current)
        every { holder.session } returns session
        every { holder.audioFormat } returns audio
        every { holder.isAudioActive() } returns true
        every { holder.isVideoActive() } returns false
        every { holder.key } returns SabrSessionKey(
            "video",
            "user",
            140,
            null,
            137,
            50,
            SabrSessionPurpose.DOWNLOAD,
        )
        every { holder.playerContextToken } returns null
        every { store.initCache } returns null
        every { session.streamState } returns state
        every { state.poToken } returns null
        every { state.getSegmentNumberAtOrAfterTimeMs(audio, 50) } returns 5
        every { state.getEndSegment(audio) } returns 5
        every { session.isBeyondEnd(any()) } returns false
        every { session.cachedBytes } returns 0
        every { session.totalResponseBytes } returns 2
        every { session.maxResponseBytes } returns 2
        every { session.getCachedSegment(any()) } answers {
            cached[firstArg<SabrSegmentRequest>().sequenceNumber]
        }
        every { session.discardCachedSegment(any()) } answers {
            cached.remove(firstArg<SabrSegmentRequest>().sequenceNumber)
        }
        coEvery { store.fetchInitializationData(holder, audio) } returns byteArrayOf(1, 2)
        val output = ByteArrayOutputStream()

        SabrDownloadStreamer(store).stream(holder, output, SabrDownloadRange(part = 1, parts = 2))

        verify { session.discardCachedSegment(match { it.sequenceNumber == 1 }) }
        verify { session.discardCachedSegment(match { it.sequenceNumber == 5 }) }
    }

    @Test
    fun `positions SABR state before pumping a missing segment`() = runTest {
        val store = mockk<SabrSessionStore>()
        val holder = mockk<SabrSessionHolder>()
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        val audio = format(140)
        val media = segment(byteArrayOf(3, 4))
        val cached = mutableMapOf<Int, SabrMediaSegment>()
        var complete = false
        every { holder.session } returns session
        every { holder.audioFormat } returns audio
        every { holder.isAudioActive() } returns true
        every { holder.isVideoActive() } returns false
        every { holder.key } returns SabrSessionKey(
            "video",
            "user",
            140,
            null,
            137,
            50,
            SabrSessionPurpose.DOWNLOAD,
        )
        every { holder.playerContextToken } returns null
        every { store.initCache } returns null
        every { session.streamState } returns state
        every { state.poToken } returns null
        every { state.getSegmentNumberAtOrAfterTimeMs(audio, 50) } returns 5
        every { state.getSegmentStartMs(audio, 5) } returns 50
        every { state.getEndSegment(audio) } returns 5
        every { state.isComplete(audio) } answers { complete }
        every { session.isBeyondEnd(any()) } returns false
        every { session.cachedBytes } returns 0
        every { session.totalResponseBytes } returns 2
        every { session.maxResponseBytes } returns 2
        every { session.diagnosticTrace } returns ""
        every { session.getCachedSegment(any()) } answers {
            cached[firstArg<SabrSegmentRequest>().sequenceNumber]
        }
        every { session.prepareForForwardJump(any(), any()) } returns Unit
        every { session.pumpOnceStreamingUntilCached(any(), any()) } answers {
            cached[5] = media
            1
        }
        every { session.discardCachedSegment(any()) } answers {
            if (cached.remove(firstArg<SabrSegmentRequest>().sequenceNumber) != null) complete = true
        }
        coEvery { store.fetchInitializationData(holder, audio) } returns byteArrayOf(1, 2)

        SabrDownloadStreamer(store).stream(
            holder,
            ByteArrayOutputStream(),
            SabrDownloadRange(part = 1, parts = 2),
        )

        verify { session.prepareForForwardJump(match { it.sequenceNumber == 5 }, 50) }
    }

    @Test
    fun `fails when an upstream pump stops responding`() = runTest {
        val store = mockk<SabrSessionStore>()
        val holder = mockk<SabrSessionHolder>()
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        val audio = format(140)
        every { holder.session } returns session
        every { holder.audioFormat } returns audio
        every { holder.isAudioActive() } returns true
        every { holder.isVideoActive() } returns false
        every { holder.key } returns SabrSessionKey(
            "video",
            "user",
            140,
            null,
            137,
            0,
            SabrSessionPurpose.DOWNLOAD,
        )
        every { holder.playerContextToken } returns null
        every { store.initCache } returns null
        every { session.streamState } returns state
        every { state.poToken } returns null
        every { state.getSegmentNumberAtOrAfterTimeMs(audio, 0) } returns 1
        every { state.getSegmentStartMs(audio, 1) } returns 0
        every { state.isComplete(audio) } returns false
        every { session.isBeyondEnd(any()) } returns false
        every { session.cachedBytes } returns 0
        every { session.getCachedSegment(any()) } returns null
        every { session.discardCachedSegment(any()) } returns Unit
        every { session.prepareForForwardJump(any(), any()) } returns Unit
        every { session.pumpOnceStreamingUntilCached(any(), any()) } answers {
            Thread.sleep(10_000)
            0
        }
        coEvery { store.fetchInitializationData(holder, audio) } returns byteArrayOf(1, 2)

        val error = try {
            SabrDownloadStreamer(store, pumpTimeoutMs = 25).stream(holder, ByteArrayOutputStream())
            null
        } catch (caught: IOException) {
            caught
        }

        assertEquals("SABR download upstream pump timed out", error?.message)
    }

    private fun format(itag: Int): YoutubeSabrFormat = mockk {
        every { this@mockk.itag } returns itag
        every { lastModified } returns 0
        every { xtags } returns null
        every { mimeType } returns "audio/mp4"
        every { audioTrackId } returns null
        every { approxDurationMs } returns 100
    }

    private fun segment(data: ByteArray): SabrMediaSegment = mockk {
        every { length } returns data.size
        every { openStream() } answers { ByteArrayInputStream(data) }
    }
}
