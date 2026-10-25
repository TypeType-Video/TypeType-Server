package dev.typetype.server.services

import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class SabrSessionIoThreadTest {
    @Test
    fun `initialization fetch runs off the call thread and honours the caller timeout`() {
        val ioThread = AtomicReference<String>()
        val format = mockk<YoutubeSabrFormat>(relaxed = true) { every { itag } returns 137 }
        val request = mockk<SabrSegmentRequest> {
            every { this@mockk.format } returns format
            every { isInitializationSegment } returns true
        }
        val session = mockk<YoutubeSabrSession>(relaxed = true) {
            every { getCachedSegment(any()) } returns null
            every { isBeyondEnd(any()) } returns false
            every { diagnosticTrace } returns ""
            every { fetchSegment(request, any()) } answers {
                ioThread.set(Thread.currentThread().name)
                Thread.sleep(3_000)
                null
            }
        }
        val holder = SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(relaxed = true),
            audioFormat = format,
            videoFormat = format,
            sessionToken = "session",
            key = SabrSessionKey("video", "user", 137, null, 137, 0L, SabrSessionPurpose.PLAYBACK),
            lastRequestAt = Instant.EPOCH,
        )
        val callThread = Executors.newSingleThreadExecutor { Thread(it, "eventLoopGroupProxy-1-1") }
        try {
            val elapsedMs = runBlocking {
                withContext(callThread.asCoroutineDispatcher()) {
                    val startedAt = System.nanoTime()
                    withTimeoutOrNull(500L) { SabrSessionPump().fetchSegment(holder, request) }
                    (System.nanoTime() - startedAt) / 1_000_000
                }
            }

            assertTrue(elapsedMs < 700L, "timeout took $elapsedMs ms")
            assertNotNull(ioThread.get())
            assertFalse(ioThread.get().startsWith("eventLoopGroupProxy"), ioThread.get())
        } finally {
            callThread.shutdownNow()
        }
    }
}
