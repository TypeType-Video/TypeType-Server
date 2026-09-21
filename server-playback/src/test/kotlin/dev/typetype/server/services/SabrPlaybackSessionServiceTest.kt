package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile as PipeProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat as PipeFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo as PipeInfo
import dev.typetype.server.sabr.YoutubeSabrClientProfile
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo

class SabrPlaybackSessionIsolationTest {
    @Test
    fun `independent playback creation isolates session state`() {
        val store = SabrSessionStore(
            tokenServiceUrl = "http://127.0.0.1:1",
            tokenClient = mockk(relaxed = true),
            sessionClient = mockk(relaxed = true),
        )
        val info = info()
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 137, isAudio = false)
        val token = token()

        val first = store.getOrCreate(
            videoId = "video",
            userId = "user",
            info = info,
            audioFormat = audio,
            videoFormat = video,
            initialToken = token,
            startPump = false,
            purpose = SabrSessionPurpose.PLAYBACK,
        )
        val second = store.getOrCreate(
            videoId = "video",
            userId = "user",
            info = info,
            audioFormat = audio,
            videoFormat = video,
            initialToken = token,
            startPump = false,
            purpose = SabrSessionPurpose.PLAYBACK,
        )

        assertNotSame(first, second)
        assertNotEquals(first.info.cpn, second.info.cpn)
        assertSame(first, store.lookupByToken(first.sessionToken))
        assertSame(second, store.lookupByToken(second.sessionToken))
        store.release()
    }

    private fun info(): YoutubeSabrInfo {
        val info = mockk<YoutubeSabrInfo>()
        every { info.videoId } returns "video"
        every { info.profile } returns YoutubeSabrClientProfile.WEB
        every { info.clientVersion } returns "1.2.3"
        every { info.cpn } returns "source-cpn"
        every { info.visitorData } returns "visitor"
        every { info.serverAbrStreamingUrl } returns "https://example.com/sabr"
        every { info.videoPlaybackUstreamerConfig } returns "config"
        every { info.formats } returns emptyList()
        val pipeInfo = mockk<PipeInfo>(relaxed = true)
        every { info.delegate } returns pipeInfo
        every { pipeInfo.profile } returns PipeProfile.WEB
        every { pipeInfo.videoId } returns "video"
        every { pipeInfo.cpn } returns "source-cpn"
        every { pipeInfo.clientVersion } returns "1.2.3"
        every { pipeInfo.visitorData } returns "visitor"
        every { pipeInfo.serverAbrStreamingUrl } returns "https://example.com/sabr"
        every { pipeInfo.videoPlaybackUstreamerConfig } returns "config"
        every { pipeInfo.formats } returns emptyList()
        return info
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>(relaxed = true)
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.isVideo } returns !isAudio
        every { format.audioTrackId } returns null
        val pipeFormat = mockk<PipeFormat>(relaxed = true)
        every { format.delegate } returns pipeFormat
        every { pipeFormat.itag } returns itag
        every { pipeFormat.isAudio } returns isAudio
        every { pipeFormat.isVideo } returns !isAudio
        return format
    }

    private fun token(): SabrTokenBundle = SabrTokenBundle(
        videoId = "video",
        visitorBoundPoToken = "visitor-token",
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = "visitor",
        videoBoundPoToken = "video-token",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )
}
