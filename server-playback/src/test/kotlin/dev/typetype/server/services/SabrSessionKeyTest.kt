package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile as PipeProfile
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat as PipeFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo as PipeInfo
import dev.typetype.server.sabr.YoutubeSabrClientProfile
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo

class SabrSessionIdentityTest {
    @Test
    fun `fresh identities use unique coherent playback nonces`() {
        val formats = listOf(mockk<YoutubeSabrFormat>())
        val source = mockk<YoutubeSabrInfo>()
        every { formats[0].itag } returns 140
        every { source.profile } returns YoutubeSabrClientProfile.WEB
        every { source.videoId } returns "video"
        every { source.clientVersion } returns "1.2.3"
        every { source.visitorData } returns "visitor"
        every { source.serverAbrStreamingUrl } returns "https://example.com/sabr?cpn=stale&foo=bar"
        every { source.videoPlaybackUstreamerConfig } returns "config"
        every { source.formats } returns formats
        val pipeFormat = mockk<PipeFormat>(relaxed = true)
        every { formats[0].delegate } returns pipeFormat
        every { pipeFormat.itag } returns 140
        val pipeInfo = mockk<PipeInfo>(relaxed = true)
        every { source.delegate } returns pipeInfo
        every { pipeInfo.profile } returns PipeProfile.WEB
        every { pipeInfo.videoId } returns "video"
        every { pipeInfo.cpn } returns "stale"
        every { pipeInfo.clientVersion } returns "1.2.3"
        every { pipeInfo.visitorData } returns "visitor"
        every { pipeInfo.serverAbrStreamingUrl } returns "https://example.com/sabr?cpn=stale&foo=bar"
        every { pipeInfo.videoPlaybackUstreamerConfig } returns "config"
        every { pipeInfo.formats } returns listOf(pipeFormat)

        val first = SabrSessionIdentity.fresh(source)
        val second = SabrSessionIdentity.fresh(source)

        assertNotEquals(first.cpn, second.cpn)
        assertEquals(first.cpn, first.serverAbrStreamingUrl!!.toHttpUrl().queryParameter("cpn"))
        assertEquals(second.cpn, second.serverAbrStreamingUrl!!.toHttpUrl().queryParameter("cpn"))
        assertEquals("bar", first.serverAbrStreamingUrl!!.toHttpUrl().queryParameter("foo"))
        assertEquals(formats[0].itag, first.formats[0].itag)
    }
}
