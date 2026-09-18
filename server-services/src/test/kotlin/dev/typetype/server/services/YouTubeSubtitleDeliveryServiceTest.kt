package dev.typetype.server.services

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class YouTubeSubtitleDeliveryServiceTest {
    @Test
    fun `VOD subtitle content is cached by stable selection`() = runTest {
        val resolutions = AtomicInteger()
        val fetches = AtomicInteger()
        val service = service(
            resolver = {
                resolutions.incrementAndGet()
                readyTrack()
            },
            fetcher = { _, _ ->
                fetches.incrementAndGet()
                YouTubeSubtitleFetchResult.Ready(VTT)
            },
        )

        val first = service.fetch(SELECTION)
        val second = service.fetch(SELECTION)

        assertTrue(first is YouTubeSubtitleContentResult.Ready)
        assertEquals(first, second)
        assertEquals(1, resolutions.get())
        assertEquals(1, fetches.get())
    }

    @Test
    fun `simultaneous subtitle requests share one upstream fetch`() = runTest {
        val resolutions = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val service = service(
            resolver = {
                resolutions.incrementAndGet()
                started.complete(Unit)
                release.await()
                readyTrack()
            },
            fetcher = { _, _ -> YouTubeSubtitleFetchResult.Ready(VTT) },
        )

        val first = async { service.fetch(SELECTION) }
        started.await()
        val second = async { service.fetch(SELECTION) }
        yield()
        release.complete(Unit)

        assertEquals(first.await(), second.await())
        assertEquals(1, resolutions.get())
    }

    @Test
    fun `expired subtitle URL is refreshed once`() = runTest {
        val resolutions = AtomicInteger()
        val fetches = AtomicInteger()
        val service = service(
            resolver = {
                resolutions.incrementAndGet()
                readyTrack()
            },
            fetcher = { _, _ ->
                if (fetches.incrementAndGet() == 1) YouTubeSubtitleFetchResult.Expired
                else YouTubeSubtitleFetchResult.Ready(VTT)
            },
        )

        val result = service.fetch(SELECTION)

        assertTrue(result is YouTubeSubtitleContentResult.Ready)
        assertEquals(2, resolutions.get())
        assertEquals(2, fetches.get())
    }

    @Test
    fun `repeated expired subtitle URL returns stable typed result`() = runTest {
        val resolutions = AtomicInteger()
        val service = service(
            resolver = {
                resolutions.incrementAndGet()
                readyTrack()
            },
            fetcher = { _, _ -> YouTubeSubtitleFetchResult.Expired },
        )

        assertEquals(YouTubeSubtitleContentResult.Expired, service.fetch(SELECTION))
        assertEquals(2, resolutions.get())
    }

    @Test
    fun `inline TTML is returned without an upstream content request`() = runTest {
        val fetches = AtomicInteger()
        val selection = SELECTION.copy(format = YouTubeSubtitleFormat.Ttml)
        val service = service(
            resolver = {
                YouTubeSubtitleResolution.Ready(
                    ResolvedYouTubeSubtitle(TTML.decodeToString(), isUrl = false, isLive = false),
                )
            },
            fetcher = { _, _ ->
                fetches.incrementAndGet()
                YouTubeSubtitleFetchResult.Unavailable
            },
        )

        val result = service.fetch(selection)

        val ready = assertInstanceOf(YouTubeSubtitleContentResult.Ready::class.java, result)
        assertTrue(ready.content.contentEquals(TTML))
        assertEquals(YouTubeSubtitleFormat.Ttml, ready.format)
        assertEquals(false, ready.isLive)
        assertEquals(0, fetches.get())
    }

    private fun service(
        resolver: suspend (YouTubeSubtitleSelection) -> YouTubeSubtitleResolution,
        fetcher: suspend (String, YouTubeSubtitleFormat) -> YouTubeSubtitleFetchResult,
    ) = YouTubeSubtitleDeliveryService(
        resolver = YouTubeSubtitleTrackResolver(resolver),
        fetcher = YouTubeSubtitleContentFetcher(fetcher),
        cache = YouTubeSubtitleCache(null),
    )

    private fun readyTrack(isLive: Boolean = false) = YouTubeSubtitleResolution.Ready(
        ResolvedYouTubeSubtitle(TIMED_TEXT_URL, isUrl = true, isLive = isLive),
    )

    private companion object {
        val VTT = "WEBVTT\n\n00:00.000 --> 00:01.000\nHello".encodeToByteArray()
        val TTML = "<?xml version=\"1.0\"?><tt><body /></tt>".encodeToByteArray()
        const val TIMED_TEXT_URL = "https://www.youtube.com/api/timedtext?v=abcdefghijk&lang=en&fmt=vtt"
        val SELECTION = YouTubeSubtitleSelection(
            videoId = "abcdefghijk",
            language = "en",
            variant = YouTubeSubtitleVariant.Manual,
            format = YouTubeSubtitleFormat.Vtt,
        )
    }
}
