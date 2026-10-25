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
    fun publicVideoUsesCachedPublicInfoWithoutFetchingAuthenticatedInfo() = runTest {
        val store = mockk<SabrSessionStore>()
        val authenticated = mockk<AuthenticatedSabrInfoService>()
        val prepared = mockk<SabrPreparedInfo>()
        coEvery { store.fetchInfo(VIDEO_ID, START_TIME_MS, true, false) } returns prepared

        val result = SabrPlaybackInfoResolver(store, authenticated) { false }
            .initial(USER_ID, VIDEO_ID, START_TIME_MS)

        assertSame(prepared, result)
        coVerify(exactly = 0) { authenticated.fetch(any(), any()) }
    }

    @Test
    fun restrictedVideoUsesAuthenticatedInfoInsteadOfPublicInfo() = runTest {
        val store = mockk<SabrSessionStore>()
        val authenticated = mockk<AuthenticatedSabrInfoService>()
        val prepared = mockk<SabrPreparedInfo>()
        coEvery { authenticated.fetch(USER_ID, VIDEO_ID) } returns AuthenticatedSabrInfoResult.Ready(prepared)

        val result = SabrPlaybackInfoResolver(store, authenticated) { true }
            .initial(USER_ID, VIDEO_ID, START_TIME_MS)

        assertSame(prepared, result)
        coVerify(exactly = 0) { store.fetchInfo(any(), any(), any(), any()) }
    }

    @Test
    fun restrictedVideoWithoutConnectedSessionDoesNotFallBackToPublicInfo() = runTest {
        val store = mockk<SabrSessionStore>()
        val authenticated = mockk<AuthenticatedSabrInfoService>()
        coEvery { authenticated.fetch(USER_ID, VIDEO_ID) } returns AuthenticatedSabrInfoResult.NotConnected

        val result = SabrPlaybackInfoResolver(store, authenticated) { true }
            .initial(USER_ID, VIDEO_ID, START_TIME_MS)

        assertNull(result)
        coVerify(exactly = 0) { store.fetchInfo(any(), any(), any(), any()) }
    }

    @Test
    fun restrictedVideoAuthenticationFailureDoesNotFallBackToPublicInfo() = runTest {
        val store = mockk<SabrSessionStore>()
        val authenticated = mockk<AuthenticatedSabrInfoService>()
        coEvery { authenticated.fetch(USER_ID, VIDEO_ID) } returns AuthenticatedSabrInfoResult.Failed

        val result = SabrPlaybackInfoResolver(store, authenticated) { true }
            .initial(USER_ID, VIDEO_ID, START_TIME_MS)

        assertNull(result)
        coVerify(exactly = 0) { store.fetchInfo(any(), any(), any(), any()) }
    }

    @Test
    fun authenticatedReplacementRemainsAuthenticated() = runTest {
        val store = mockk<SabrSessionStore>()
        val authenticated = mockk<AuthenticatedSabrInfoService>()
        val prepared = mockk<SabrPreparedInfo>()
        val holder = mockk<SabrSessionHolder> {
            every { this@mockk.source } returns SabrPreparedSource.AUTHENTICATED_YOUTUBE
            every { key } returns SabrSessionKey(VIDEO_ID, USER_ID, 140, null, 137, 0L)
        }
        coEvery { authenticated.fetch(USER_ID, VIDEO_ID) } returns AuthenticatedSabrInfoResult.Ready(prepared)

        val result = SabrPlaybackInfoResolver(store, authenticated)
            .replacement(holder, START_TIME_MS)

        assertSame(prepared, result)
        coVerify(exactly = 0) { store.fetchInfo(any(), any(), any(), any()) }
    }

    private companion object {
        const val USER_ID = "user-id"
        const val VIDEO_ID = "video-id"
        const val START_TIME_MS = 5_000L
    }
}
