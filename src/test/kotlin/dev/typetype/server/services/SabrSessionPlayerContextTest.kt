package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrSessionPlayerContextTest {
    @Test
    fun `holder exposes only its own token during extractor calls`() {
        val first = holder(token("first", "first-token"))
        val second = holder(token("second", "second-token"))

        first.withPlayerContext {
            assertEquals("first", currentToken()?.visitorData)
            second.withPlayerContext {
                assertEquals("second", currentToken()?.visitorData)
            }
            assertEquals("first", currentToken()?.visitorData)
        }

        assertNull(currentToken())
    }

    private fun holder(token: SabrTokenBundle): SabrSessionHolder {
        val audio = format(140, audio = true)
        val video = format(137, audio = false)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        val session = mockk<YoutubeSabrSession> {
            every { this@mockk.streamState } returns streamState
        }
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
            playerContextToken = token,
        )
    }

    private fun format(itag: Int, audio: Boolean): YoutubeSabrFormat = mockk(relaxed = true) {
        every { this@mockk.itag } returns itag
        every { isAudio } returns audio
        every { isVideo } returns !audio
    }

    private fun currentToken() = TypetypeYoutubeSessionPoTokenProvider.getSessionPoToken(
        "MWEB",
        "2.20260801.00.00",
        "test-user-agent",
        Localization("en", "US"),
        ContentCountry("US"),
        false,
    )

    private fun token(visitorData: String, playerToken: String) = SabrTokenBundle(
        videoId = "video",
        visitorBoundPoToken = playerToken,
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = visitorData,
        videoBoundPoToken = "video-token",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )
}
