package dev.typetype.server.services

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException

class PipePipeYoutubeLiveChatServiceTest {
    private val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
    private val streamExtractor = mockk<StreamExtractor>()
    private val chatExtractor = mockk<BulletCommentsExtractor>()
    private val token = SabrTokenBundle("live", "visitor-pot", byteArrayOf(1), "visitor", "media-pot", byteArrayOf(2))

    @BeforeEach
    fun setUp() {
        val registeredService = YoutubeService(0)
        mockkStatic(NewPipe::class)
        every { NewPipe.getServiceByUrl(URL) } returns registeredService
        mockkConstructor(YoutubeService::class)
        every { anyConstructed<YoutubeService>().getStreamExtractor(URL) } returns streamExtractor
        every { anyConstructed<YoutubeService>().getBulletCommentsExtractor(URL) } returns chatExtractor
        every { streamExtractor.id } returns "live"
        every { tokenClient.fetch("live") } returns token
        every { streamExtractor.fetchPage() } answers { assertGuestPlayerContext() }
        val info = mockk<StreamInfo>()
        every { info.streamType } returns StreamType.LIVE_STREAM
        every { info.startAt } returns 0L
        mockkStatic(StreamInfo::class)
        every { StreamInfo.getInfo(streamExtractor) } answers {
            assertGuestPlayerContext()
            info
        }
        mockkStatic(BulletCommentsInfo::class)
        every { BulletCommentsInfo.getInfo(chatExtractor) } returns mockk()
        every { chatExtractor.isDisabled } returns false
        every { chatExtractor.close() } just runs
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `guest extraction carries the visitor-bound player token`() = runBlocking {
        val service = PipePipeYoutubeLiveChatService(tokenClient)
        try {
            val result = service.openSession(URL)
            assertTrue(result is YoutubeLiveChatOpenResult.Opened)
            (result as YoutubeLiveChatOpenResult.Opened).session.close()
            verify(exactly = 1) { tokenClient.fetch("live") }
            verify(exactly = 1) { streamExtractor.fetchPage() }
            verify(exactly = 1) { chatExtractor.close() }
        } finally {
            service.close()
        }
    }

    @Test
    fun `missing context does not send an unprotected player request or leak capacity`() = runBlocking {
        every { tokenClient.fetch("live") } returns null
        val service = PipePipeYoutubeLiveChatService(tokenClient)
        try {
            repeat(13) {
                assertTrue(service.openSession(URL) is YoutubeLiveChatOpenResult.Unavailable)
            }
            verify(exactly = 0) { streamExtractor.fetchPage() }
        } finally {
            service.close()
        }
    }

    @Test
    fun `player failure releases session capacity`() = runBlocking {
        every { streamExtractor.fetchPage() } answers {
            assertGuestPlayerContext()
            throw IOException("player request failed")
        }
        val service = PipePipeYoutubeLiveChatService(tokenClient)
        try {
            repeat(13) {
                assertTrue(service.openSession(URL) is YoutubeLiveChatOpenResult.Unavailable)
            }
            verify(exactly = 13) { tokenClient.fetch("live") }
        } finally {
            service.close()
        }
    }

    private fun assertGuestPlayerContext() {
        val context = requireNotNull(
            TypetypeYoutubeSessionPoTokenProvider.getSessionPoToken(
                "MWEB", "version", null, Localization.DEFAULT, ContentCountry.DEFAULT, false,
            ),
        )
        assertEquals("visitor", context.visitorData)
        assertEquals("visitor-pot", context.poToken)
    }

    private companion object {
        const val URL = "https://www.youtube.com/watch?v=live"
    }
}
