package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SabrInfoFetcherCoalescingTest {
    @Test
    fun `concurrent info fetches for one video share one token session call`() = runBlocking {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        val sessionClient = mockk<TypetypeTokenYoutubeSessionClient>()
        coEvery { sessionClient.fetchPlaybackSession(VIDEO_ID, false) } coAnswers {
            delay(100)
            session()
        }
        val fetcher = SabrInfoFetcher(tokenClient, sessionClient)

        val results = List(20) { async(Dispatchers.Default) { fetcher.fetchInfo(VIDEO_ID) } }.awaitAll()

        assertTrue(results.all { it != null })
        coVerify(exactly = 1) { sessionClient.fetchPlaybackSession(VIDEO_ID, false) }
    }

    @Test
    fun `a blocked token call is interrupted at the info timeout`() = runBlocking {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        every { tokenClient.fetch(VIDEO_ID) } answers {
            Thread.sleep(5_000)
            null
        }
        val fetcher = SabrInfoFetcher(tokenClient, infoTimeoutMs = 300L)
        val startedAt = System.nanoTime()

        val result = fetcher.fetchInfo(VIDEO_ID)

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertNull(result)
        assertTrue(elapsedMs < 1_000L, "info fetch took $elapsedMs ms")
    }

    private fun session(): TokenYoutubeSession {
        val video = mockk<YoutubeSabrFormat>(relaxed = true) { every { isVideo } returns true }
        val audio = mockk<YoutubeSabrFormat>(relaxed = true) { every { isAudio } returns true }
        val info = mockk<YoutubeSabrInfo>(relaxed = true) {
            every { formats } returns listOf(video, audio)
            every { visitorData } returns "visitor"
        }
        return TokenYoutubeSession(
            info = info,
            token = SabrTokenBundle(VIDEO_ID, "pot", byteArrayOf(1), "visitor", "vpot", byteArrayOf(2)),
            title = "title",
            author = "author",
            channelId = "channel",
            channelAvatarUrl = "",
            description = "",
            durationMs = 1_000L,
            viewCount = 1L,
            thumbnailUrl = "",
            tags = emptyList(),
            isLive = false,
            isLiveContent = false,
        )
    }

    private companion object {
        const val VIDEO_ID = "video"
    }
}
