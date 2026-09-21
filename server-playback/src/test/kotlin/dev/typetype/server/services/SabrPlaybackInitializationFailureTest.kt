package dev.typetype.server.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SabrPlaybackInfoResolverTest {
    @Test
    fun `connected account uses authenticated playback info`() = runTest {
        val store = mockk<SabrSessionStore>()
        val authenticated = mockk<AuthenticatedSabrInfoService>()
        val prepared = mockk<SabrPreparedInfo>()
        coEvery { authenticated.fetch(USER_ID, VIDEO_ID) } returns AuthenticatedSabrInfoResult.Ready(prepared)

        val result = SabrPlaybackInfoResolver(store, authenticated).initial(USER_ID, VIDEO_ID, 0L)

        assertSame(prepared, result)
        coVerify(exactly = 0) { store.fetchInfo(any(), any(), any(), any()) }
    }

    @Test
    fun `authenticated failure never falls back to public playback info`() = runTest {
        val store = mockk<SabrSessionStore>()
        val authenticated = mockk<AuthenticatedSabrInfoService>()
        coEvery { authenticated.fetch(USER_ID, VIDEO_ID) } returns AuthenticatedSabrInfoResult.Failed

        val result = SabrPlaybackInfoResolver(store, authenticated).initial(USER_ID, VIDEO_ID, 0L)

        assertNull(result)
        coVerify(exactly = 0) { store.fetchInfo(any(), any(), any(), any()) }
    }

    @Test
    fun `account without YouTube connection uses public playback info`() = runTest {
        val store = mockk<SabrSessionStore>()
        val authenticated = mockk<AuthenticatedSabrInfoService>()
        val prepared = mockk<SabrPreparedInfo>()
        coEvery { authenticated.fetch(USER_ID, VIDEO_ID) } returns AuthenticatedSabrInfoResult.NotConnected
        coEvery { store.fetchInfo(VIDEO_ID, 5_000L, true, false) } returns prepared

        val result = SabrPlaybackInfoResolver(store, authenticated).initial(USER_ID, VIDEO_ID, 5_000L)

        assertSame(prepared, result)
    }

    @Test
    fun `authenticated replacement remains authenticated`() = runTest {
        val store = mockk<SabrSessionStore>()
        val authenticated = mockk<AuthenticatedSabrInfoService>()
        val prepared = mockk<SabrPreparedInfo>()
        val holder = holder(SabrPreparedSource.AUTHENTICATED_YOUTUBE)
        coEvery { authenticated.fetch(USER_ID, VIDEO_ID) } returns AuthenticatedSabrInfoResult.Ready(prepared)

        val result = SabrPlaybackInfoResolver(store, authenticated).replacement(holder, 60_000L)

        assertSame(prepared, result)
        coVerify(exactly = 0) { store.fetchInfo(any(), any(), any(), any()) }
    }

    @Test
    fun `authenticated replacement failure never changes source`() = runTest {
        val store = mockk<SabrSessionStore>()
        val authenticated = mockk<AuthenticatedSabrInfoService>()
        val holder = holder(SabrPreparedSource.AUTHENTICATED_YOUTUBE)
        coEvery { authenticated.fetch(USER_ID, VIDEO_ID) } returns AuthenticatedSabrInfoResult.Failed

        val result = SabrPlaybackInfoResolver(store, authenticated).replacement(holder, 60_000L)

        assertNull(result)
        coVerify(exactly = 0) { store.fetchInfo(any(), any(), any(), any()) }
    }

    private fun holder(source: SabrPreparedSource): SabrSessionHolder = mockk {
        every { this@mockk.source } returns source
        every { key } returns SabrSessionKey(VIDEO_ID, USER_ID, 140, null, 137, 0L)
    }

    private companion object {
        const val USER_ID = "user-id"
        const val VIDEO_ID = "video-id"
    }
}
