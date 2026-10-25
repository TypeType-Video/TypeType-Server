package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SabrPreparedInfoEpochTest {
    @Test
    fun `prepared info from before a provider session renewal is a miss`() = runTest {
        val epoch = ProviderSessionEpoch()
        val repository = SabrInfoRepository(SabrPreparedInfoCache(), sharedCache = null, sessionEpoch = epoch)
        val prepared = prepared(token(expiresAtMs = null))
        repository.putPrepared(VIDEO_ID, startTimeMs = 0L, prepared)

        assertSame(prepared, repository.local(VIDEO_ID, startTimeMs = 0L))
        epoch.advance()

        assertNull(repository.local(VIDEO_ID, startTimeMs = 0L))
    }

    @Test
    fun `prepared info with an expired token is a miss`() {
        val cache = SabrPreparedInfoCache(clock = { 2_000L })
        cache.put(VIDEO_ID, startTimeMs = 0L, prepared(token(expiresAtMs = 1_000L)))

        assertNull(cache.get(VIDEO_ID, startTimeMs = 0L))
    }

    @Test
    fun `authenticated info from before a provider session renewal is reloaded`() = runTest {
        val cache = AuthenticatedSabrInfoCache()
        var loads = 0
        val ready = AuthenticatedSabrInfoResult.Ready(prepared(null))

        cache.getOrLoad("user", "identity", VIDEO_ID, epoch = 0L) { loads++; ready }
        cache.getOrLoad("user", "identity", VIDEO_ID, epoch = 0L) { loads++; ready }
        cache.getOrLoad("user", "identity", VIDEO_ID, epoch = 1L) { loads++; ready }

        assertEquals(2, loads)
    }

    private fun prepared(token: SabrTokenBundle?): SabrPreparedInfo {
        val audio = mockk<YoutubeSabrFormat>(relaxed = true) { every { isAudio } returns true }
        val video = mockk<YoutubeSabrFormat>(relaxed = true) { every { isVideo } returns true }
        val info = mockk<YoutubeSabrInfo>(relaxed = true) { every { formats } returns listOf(audio, video) }
        return SabrPreparedInfo(info, token)
    }

    private fun token(expiresAtMs: Long?): SabrTokenBundle = SabrTokenBundle(
        videoId = VIDEO_ID,
        visitorBoundPoToken = "visitor-token",
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = "visitor",
        videoBoundPoToken = "video-token",
        videoBoundPoTokenBytes = byteArrayOf(2),
        expiresAtMs = expiresAtMs,
    )

    private companion object {
        const val VIDEO_ID = "video"
    }
}
