package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamType

class PipePipeLiveHlsStreamServiceTest {
    @Test
    fun returnsLiveHlsWithoutEnumeratingFormats() = runTest {
        val extractor = mockk<StreamExtractor>(relaxed = true)
        every { extractor.fetchPage() } just runs
        every { extractor.streamType } returns StreamType.LIVE_STREAM
        every { extractor.hlsUrl } returns HLS_URL
        every { extractor.requiresMembership() } returns false
        every { extractor.id } returns VIDEO_ID
        every { extractor.name } returns "Live title"
        val service = PipePipeLiveHlsStreamService { extractor }

        val response = (service.getStreamInfo(YOUTUBE_URL) as ExtractionResult.Success).data

        assertEquals(HLS_URL, response.hlsUrl)
        assertTrue(response.isLive)
        assertTrue(response.hasLiveManifest)
        assertTrue(response.videoStreams.isEmpty())
        assertTrue(response.audioStreams.isEmpty())
        verify(exactly = 1) { extractor.fetchPage() }
        verify(exactly = 0) { extractor.audioStreams }
        verify(exactly = 0) { extractor.videoStreams }
    }

    @Test
    fun rejectsVodBeforeReadingManifest() = runTest {
        val extractor = mockk<StreamExtractor>(relaxed = true)
        every { extractor.fetchPage() } just runs
        every { extractor.streamType } returns StreamType.VIDEO_STREAM
        val service = PipePipeLiveHlsStreamService { extractor }

        val result = service.getStreamInfo(YOUTUBE_URL)

        assertTrue(result is ExtractionResult.Failure)
        assertEquals("live_stream_unavailable", (result as ExtractionResult.Failure).code)
        verify(exactly = 0) { extractor.hlsUrl }
    }

    @Test
    fun doesNotExposeMembersOnlyManifest() = runTest {
        val extractor = mockk<StreamExtractor>(relaxed = true)
        every { extractor.fetchPage() } just runs
        every { extractor.streamType } returns StreamType.LIVE_STREAM
        every { extractor.hlsUrl } returns HLS_URL
        every { extractor.requiresMembership() } returns true
        val service = PipePipeLiveHlsStreamService { extractor }

        val result = service.getStreamInfo(YOUTUBE_URL)

        assertTrue(result is ExtractionResult.Failure)
    }

    private companion object {
        const val VIDEO_ID = "GlzleRbo5E0"
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=$VIDEO_ID"
        const val HLS_URL = "https://manifest.googlevideo.com/live.m3u8"
    }
}
