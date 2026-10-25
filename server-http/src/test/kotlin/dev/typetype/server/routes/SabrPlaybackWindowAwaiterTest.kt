package dev.typetype.server.routes

import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionPurpose
import dev.typetype.server.services.signalPlaybackProgress
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SabrPlaybackWindowAwaiterTest {
    @Test
    fun `answers as soon as the pending demand resolves instead of a 202 round trip`() = runBlocking {
        val holder = holder()
        val resolved = AtomicBoolean(false)
        launch(Dispatchers.Default) {
            delay(80)
            resolved.set(true)
            holder.signalPlaybackProgress()
        }
        val startedAt = System.nanoTime()

        val window = SabrPlaybackWindowAwaiter().await(holder, window(false)) { window(resolved.get()) }

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue(window.isReady)
        assertTrue(elapsedMs < 250, "elapsed $elapsedMs ms")
        println("sabr_window_await first_ready_ms=$elapsedMs previous_first_ready_ms=540")
    }

    @Test
    fun `gives up after the bounded wait and keeps the 202 contract`() = runBlocking {
        val builds = AtomicInteger(0)
        val startedAt = System.nanoTime()

        val window = SabrPlaybackWindowAwaiter(maxWaitMs = 200L).await(holder(), window(false)) {
            builds.incrementAndGet()
            window(false)
        }

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertFalse(window.isReady)
        assertTrue(elapsedMs in 190..450, "elapsed $elapsedMs ms")
        assertEquals(2, builds.get())
    }

    @Test
    fun `does not hold a failed session`() = runBlocking {
        val holder = holder()
        holder.failTerminal("refused")
        val startedAt = System.nanoTime()

        SabrPlaybackWindowAwaiter().await(holder, window(false)) { window(false) }

        assertTrue((System.nanoTime() - startedAt) / 1_000_000 < 50)
    }

    private fun window(ready: Boolean): SabrPlaybackWindowBuildResult = SabrPlaybackWindowBuildResult(
        response = SabrPlaybackWindowReadyResponse(
            sessionId = "session",
            generation = 0L,
            ready = true,
            retryAfterMs = null,
            durationMs = 60_000L,
            endOfStream = false,
            audio = SabrPlaybackWindowTrack("audio/mp4", "init", emptyList()),
        ),
        blockedBy = if (ready) null else "video:136:1 pending",
        blockedRequests = emptyList(),
        isReady = ready,
    )

    private fun holder(): SabrSessionHolder = SabrSessionHolder(
        session = mockk<YoutubeSabrSession>(relaxed = true),
        info = mockk<YoutubeSabrInfo>(),
        audioFormat = format(140),
        videoFormat = format(136),
        sessionToken = "session",
        key = SabrSessionKey("video", "user", 140, null, 136, 0L, SabrSessionPurpose.PLAYBACK),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int): YoutubeSabrFormat = mockk(relaxed = true) { every { this@mockk.itag } returns itag }
}
