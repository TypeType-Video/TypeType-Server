package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.localization.Localization
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState

class SabrAdaptiveInitializationTest {
    @Test
    fun `fetches initialization range with the active streaming token`() = runBlocking {
        val fixture = fixture(byteArrayOf(1, 2, 3))
        val expected = byteArrayOf(4, 5, 6)
        every {
            fixture.session.fetchInitializationData(
                fixture.format,
                any<Localization>(),
                2_000L,
                match { it.contentEquals(fixture.poToken) },
            )
        } returns expected

        val actual = SabrAdaptiveInitialization.fetchRange(fixture.holder, fixture.format, 2_000L)

        assertArrayEquals(expected, actual)
    }

    @Test
    fun `returns null when the adaptive range request fails`() = runBlocking {
        val fixture = fixture(byteArrayOf(1))
        every {
            fixture.session.fetchInitializationData(
                fixture.format,
                any<Localization>(),
                2_000L,
                any(),
            )
        } throws java.io.IOException("range unavailable")

        assertNull(SabrAdaptiveInitialization.fetchRange(fixture.holder, fixture.format, 2_000L))
    }

    @Test
    fun `does not request a range without a streaming token`() = runBlocking {
        val fixture = fixture(null)

        assertNull(SabrAdaptiveInitialization.fetchRange(fixture.holder, fixture.format, 2_000L))
        verify(exactly = 0) {
            fixture.session.fetchInitializationData(any(), any(), any(), any())
        }
    }

    private fun fixture(poToken: ByteArray?): Fixture {
        val holder = mockk<SabrSessionHolder>()
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        val format = mockk<YoutubeSabrFormat>()
        every { holder.session } returns session
        every { holder.playerContextToken } returns null
        every { holder.info } returns mockk<YoutubeSabrInfo>()
        every { session.streamState } returns state
        every { state.poToken } returns poToken
        return Fixture(holder, session, format, poToken ?: byteArrayOf())
    }

    private data class Fixture(
        val holder: SabrSessionHolder,
        val session: YoutubeSabrSession,
        val format: YoutubeSabrFormat,
        val poToken: ByteArray,
    )
}
