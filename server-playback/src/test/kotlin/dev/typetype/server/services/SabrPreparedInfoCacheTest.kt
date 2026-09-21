package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.exceptions.AntiBotException
import dev.typetype.server.sabr.SabrProtocolException
import dev.typetype.server.sabr.YoutubeSabrClientProfile
import dev.typetype.server.sabr.YoutubeSabrInfo
import java.io.IOException

class SabrPlayerContextRecoveryTest {
    @Test
    fun `refreshes rejected player context once`() {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        val initial = token("old-visitor")
        val refreshed = token("fresh-visitor")
        val info = mockk<YoutubeSabrInfo>()
        every { tokenClient.fetch("video", forceRefresh = true, refreshVideo = false) } returns refreshed
        val visitors = mutableListOf<String>()
        val probe = SabrPlayerInfoProbe { _, profile, token ->
            visitors += token.visitorData
            if (token === initial) throw missingStreamingData(profile)
            info
        }

        val result = SabrPlayerContextRecovery("video", initial, tokenClient, probe)
            .fetch(YoutubeSabrClientProfile.WEB)

        val success = assertInstanceOf(SabrPlayerProbeResult.Success::class.java, result)
        assertSame(info, success.info)
        assertSame(refreshed, success.token)
        assertTrue(success.contextRefreshed)
        assertEquals(listOf("old-visitor", "fresh-visitor"), visitors)
        verify(exactly = 1) { tokenClient.fetch("video", forceRefresh = true, refreshVideo = false) }
    }

    @Test
    fun `refreshes typed anti bot rejection`() {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        val refreshed = token("fresh-visitor")
        every { tokenClient.fetch("video", forceRefresh = true, refreshVideo = false) } returns refreshed
        var attempts = 0
        val probe = SabrPlayerInfoProbe { _, _, _ ->
            attempts++
            if (attempts == 1) throw AntiBotException("Sign in to confirm you're not a bot")
            mockk()
        }

        val result = SabrPlayerContextRecovery("video", token("old-visitor"), tokenClient, probe)
            .fetch(YoutubeSabrClientProfile.MWEB)

        assertInstanceOf(SabrPlayerProbeResult.Success::class.java, result)
        assertEquals(2, attempts)
    }

    @Test
    fun `does not refresh network failure`() {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>(relaxed = true)
        val failure = IOException("network unavailable")
        val probe = SabrPlayerInfoProbe { _, _, _ -> throw failure }

        val result = SabrPlayerContextRecovery("video", token("visitor"), tokenClient, probe)
            .fetch(YoutubeSabrClientProfile.WEB)

        val failed = assertInstanceOf(SabrPlayerProbeResult.Failure::class.java, result)
        assertSame(failure, failed.error)
        verify(exactly = 0) { tokenClient.fetch(any(), any(), any()) }
    }

    @Test
    fun `does not refresh unrelated protocol failure`() {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>(relaxed = true)
        val failure = SabrProtocolException("Missing serverAbrStreamingUrl")
        val probe = SabrPlayerInfoProbe { _, _, _ -> throw failure }

        val result = SabrPlayerContextRecovery("video", token("visitor"), tokenClient, probe)
            .fetch(YoutubeSabrClientProfile.WEB)

        val failed = assertInstanceOf(SabrPlayerProbeResult.Failure::class.java, result)
        assertSame(failure, failed.error)
        verify(exactly = 0) { tokenClient.fetch(any(), any(), any()) }
    }

    @Test
    fun `propagates cancellation without refreshing`() {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>(relaxed = true)
        val probe = SabrPlayerInfoProbe { _, _, _ -> throw CancellationException("cancelled") }
        val recovery = SabrPlayerContextRecovery("video", token("visitor"), tokenClient, probe)

        assertThrows(CancellationException::class.java) {
            recovery.fetch(YoutubeSabrClientProfile.WEB)
        }
        verify(exactly = 0) { tokenClient.fetch(any(), any(), any()) }
    }

    @Test
    fun `shares one refresh budget across profiles`() {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        every { tokenClient.fetch("video", forceRefresh = true, refreshVideo = false) } returns token("fresh")
        val probe = SabrPlayerInfoProbe { _, profile, _ -> throw missingStreamingData(profile) }
        val recovery = SabrPlayerContextRecovery("video", token("old"), tokenClient, probe)

        val web = recovery.fetch(YoutubeSabrClientProfile.WEB)
        val mweb = recovery.fetch(YoutubeSabrClientProfile.MWEB)

        val failedWeb = assertInstanceOf(SabrPlayerProbeResult.Failure::class.java, web)
        assertTrue(failedWeb.contextRefreshAttempted)
        assertInstanceOf(SabrPlayerProbeResult.Failure::class.java, mweb)
        verify(exactly = 1) { tokenClient.fetch("video", forceRefresh = true, refreshVideo = false) }
    }

    private fun missingStreamingData(profile: YoutubeSabrClientProfile): SabrProtocolException =
        SabrProtocolException("Player response has no streamingData for $profile")

    private fun token(visitorData: String): SabrTokenBundle = SabrTokenBundle(
        videoId = "video",
        visitorBoundPoToken = "player-$visitorData",
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = visitorData,
        videoBoundPoToken = "video-$visitorData",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )
}
