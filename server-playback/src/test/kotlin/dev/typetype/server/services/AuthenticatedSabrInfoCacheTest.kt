package dev.typetype.server.services

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.models.StreamResponse
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Duration

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AuthenticatedSabrInfoCacheTest {
    @Test
    fun `concurrent timeout callers share one bounded load`() = runTest {
        val cache = AuthenticatedSabrInfoCache(timeoutMs = 100L)
        var loads = 0
        val calls = List(3) {
            async { cache.getOrLoad(credentials, VIDEO_ID) { loads++; awaitCancellation() } }
        }
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()

        calls.forEach { call ->
            assertInstanceOf(TimeoutCancellationException::class.java, runCatching { call.await() }.exceptionOrNull())
        }
        assertEquals(1, loads)
    }

    @Test
    fun `cancelled leader does not poison a following caller`() = runTest {
        val cache = AuthenticatedSabrInfoCache(timeoutMs = 100L)
        var loads = 0
        val leader = async { cache.getOrLoad(credentials, VIDEO_ID) { loads++; awaitCancellation() } }
        runCurrent()
        leader.cancelAndJoin()

        val follower = async { cache.getOrLoad(credentials, VIDEO_ID) { loads++; awaitCancellation() } }
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()

        assertInstanceOf(TimeoutCancellationException::class.java, runCatching { follower.await() }.exceptionOrNull())
        assertEquals(2, loads)
    }

    @Test
    fun `successful concurrent callers share one ready result`() = runTest {
        val cache = AuthenticatedSabrInfoCache(Duration.ofMinutes(1), timeoutMs = 100L)
        var loads = 0
        val prepared = mockkPrepared()
        val calls = List(3) {
            async {
                cache.getOrLoad(credentials, VIDEO_ID) {
                    loads++
                    advanceTimeBy(50L)
                    AuthenticatedSabrInfoResult.Ready(prepared)
                }
            }
        }
        advanceTimeBy(50L)
        runCurrent()

        calls.forEach { call -> assertEquals(prepared, (call.await() as AuthenticatedSabrInfoResult.Ready).prepared) }
        assertEquals(1, loads)
    }

    @Test
    fun authenticatedEntriesAreIsolatedByAccountAndIdentity() = runTest {
        val cache = AuthenticatedSabrInfoCache()
        val first = AuthenticatedSabrInfoResult.Ready(mockkPrepared())
        val second = AuthenticatedSabrInfoResult.Ready(mockkPrepared())
        var loads = 0

        val firstResult = cache.getOrLoad("user-a", "identity-a", VIDEO_ID) { loads++; first }
        val otherUser = cache.getOrLoad("user-b", "identity-a", VIDEO_ID) { loads++; second }
        val otherIdentity = cache.getOrLoad("user-a", "identity-b", VIDEO_ID) { loads++; second }
        val cached = cache.getOrLoad("user-a", "identity-a", VIDEO_ID) { loads++; second }

        assertSame(first, firstResult)
        assertSame(second, otherUser)
        assertSame(second, otherIdentity)
        assertSame(first, cached)
        assertEquals(3, loads)
    }

    private fun mockkPrepared(): SabrPreparedInfo = SabrPreparedInfo(mockk(relaxed = true), null)

    @Test
    fun `authenticated entries expire before their signed streaming url`() = runTest {
        var now = 600_000L
        val cache = AuthenticatedSabrInfoCache(
            ttl = Duration.ofMinutes(5),
            timeoutMs = 100L,
            nowMillis = { now },
        )
        val info = mockk<YoutubeSabrInfo> {
            every { serverAbrStreamingUrl } returns "https://example.com/sabr?expire=1100"
        }
        val response = mockk<StreamResponse>(relaxed = true) {
            every { hlsUrl } returns "https://example.com/hls?expire=1000"
            every { dashMpdUrl } returns ""
        }
        val result = AuthenticatedSabrInfoResult.Ready(SabrPreparedInfo(info, null), response)
        var loads = 0

        cache.getOrLoad("user", "oauth-fingerprint", VIDEO_ID) { loads++; result }
        now = 700_000L
        cache.getOrLoad("user", "oauth-fingerprint", VIDEO_ID) { loads++; result }

        assertEquals(2, loads)
    }

    private companion object {
        val credentials = YoutubeSessionCredentials(
            userId = "user",
            fingerprint = "fingerprint",
            cookies = "SID=session-cookie",
            poToken = "session-player-token",
        )
        const val VIDEO_ID = "video-id"
    }
}
