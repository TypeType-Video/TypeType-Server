package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrMediaHeader
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrSegmentCacheTest {
    @Test
    fun `vod cache observes media without copying segment bytes`() {
        val segmentCache = SabrSegmentCache()
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video, SabrSessionPurpose.PLAYBACK)
        val bytes = byteArrayOf(1, 2, 3)
        val segment = segment(itag = 140, sequence = 4, bytes = bytes)
        val request = SabrSegmentRequest.media(audio, 4)

        segmentCache.put(holder, segment)
        assertNull(segmentCache.get(holder, request))
        assertEquals(4, holder.observedMediaSegment(audio)?.header?.sequenceNumber)
        verify(exactly = 0) { segment.data }
    }

    @Test
    fun `live cache separates initialization from mp4 media`() {
        val segmentCache = SabrSegmentCache()
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val holder = holder(audio, video).also { it.markExpectedLive() }
        val initialization = mp4Box("ftyp", 1) + mp4Box("moov", 2)
        val media = mp4Box("moof", 3) + mp4Box("mdat", 4)
        val request = SabrSegmentRequest.media(video, 7)

        segmentCache.put(holder, segment(137, 7, initialization + media))

        assertArrayEquals(initialization, holder.liveInitialization(video))
        assertArrayEquals(media, requireNotNull(segmentCache.get(holder, request)).bytes)
    }

    private fun holder(
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        purpose: SabrSessionPurpose = SabrSessionPurpose.MANIFEST,
    ): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns state
        every { state.setActiveTrackTypes(any(), any()) } returns Unit
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "token",
            key = SabrSessionKey("video", "user", 140, null, 137, 0L, purpose),
            lastRequestAt = Instant.EPOCH,
        )
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.isVideo } returns !isAudio
        every { format.lastModified } returns 7L
        every { format.xtags } returns null
        every { format.mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        return format
    }

    private fun segment(itag: Int, sequence: Int, bytes: ByteArray): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.itag } returns itag
        every { header.sequenceNumber } returns sequence
        every { header.isInitSegment } returns false
        every { header.startMs } returns 1234L
        every { header.durationMs } returns 9985L
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        every { segment.data } returns bytes
        every { segment.length } returns bytes.size
        return segment
    }

    private fun mp4Box(type: String, value: Byte): ByteArray =
        byteArrayOf(0, 0, 0, 9) + type.toByteArray(Charsets.US_ASCII) + byteArrayOf(value)
}
