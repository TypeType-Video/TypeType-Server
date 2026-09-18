package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat

class SabrPlaybackDiagnosticsTest {
    @Test
    fun tokenBindingMismatchStopsTargetedFetch(): Unit {
        val holder = mockk<SabrSessionHolder>(relaxed = true)
        val request = mockk<SabrSegmentRequest>()
        val format = mockk<YoutubeSabrFormat>()
        every { holder.sessionToken } returns "binding-mismatch-session"
        every { request.format } returns format
        every { request.sequenceNumber } returns 12
        every { format.isAudio } returns false
        every { format.itag } returns 137

        SabrPlaybackDiagnostics.record(holder, request, SABR_TOKEN_BINDING_FAILURE)

        verify(exactly = 1) {
            holder.failTerminal(match { it.contains(SABR_TOKEN_BINDING_FAILURE) })
        }
    }
}
