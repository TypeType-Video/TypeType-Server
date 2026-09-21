package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class SabrProtectedContextRecoveryTest {
    @Test
    fun `refreshes the active rejected context`() {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        every { tokenClient.fetch("video") } returns token("rejected")
        every { tokenClient.fetch("video", forceRefresh = true) } returns token("fresh")

        SabrProtectedContextRecovery(tokenClient).refreshIfRejected("video", "rejected")

        verify(exactly = 1) { tokenClient.fetch("video", forceRefresh = true) }
    }

    @Test
    fun `keeps a context already refreshed by another recovery`() {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        every { tokenClient.fetch("video") } returns token("fresh")

        SabrProtectedContextRecovery(tokenClient).refreshIfRejected("video", "rejected")

        verify(exactly = 0) { tokenClient.fetch("video", forceRefresh = true) }
    }

    @Test
    fun `refreshes when the current context cannot be read`() {
        val tokenClient = mockk<TypetypeTokenSabrTokenClient>()
        every { tokenClient.fetch("video") } returns null
        every { tokenClient.fetch("video", forceRefresh = true) } returns token("fresh")

        SabrProtectedContextRecovery(tokenClient).refreshIfRejected("video", "rejected")

        verify(exactly = 1) { tokenClient.fetch("video", forceRefresh = true) }
    }

    private fun token(visitorData: String): SabrTokenBundle = SabrTokenBundle(
        videoId = "video",
        visitorBoundPoToken = "player-$visitorData",
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = visitorData,
        videoBoundPoToken = "video-$visitorData",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )
}
