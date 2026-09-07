package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState
import java.time.Instant
import java.util.concurrent.CountDownLatch

class SabrSessionRegistryTest {
    @Test
    fun `capacity eviction removes oldest session`() {
        val registry = SabrSessionRegistry()
        val oldestKey = key("oldest")
        val idleKey = key("idle")
        val oldest = holder("oldest", Instant.EPOCH)
        val idle = holder("idle", Instant.EPOCH.plusSeconds(1))

        registry.put(oldestKey, oldest)
        registry.put(idleKey, idle)
        registry.ensureCapacity(2)

        assertNull(registry.get(oldestKey))
        assertSame(idle, registry.get(idleKey))
    }

    @Test
    fun `idle eviction removes stale sessions`() {
        val registry = SabrSessionRegistry()
        val staleKey = key("stale")
        val stale = holder("stale", Instant.EPOCH)

        registry.put(staleKey, stale)
        registry.evictIdle(Instant.EPOCH.plusSeconds(60))

        assertNull(registry.get(staleKey))
    }

    @Test
    fun `lookup by token returns matching playback session`() {
        val registry = SabrSessionRegistry()
        val key = key("playback")
        val holder = holder("playback", Instant.EPOCH)

        registry.put(key, holder)

        assertSame(holder, registry.lookupByToken("token-playback"))
    }

    @Test
    fun `healthy session remains reusable`() {
        val registry = SabrSessionRegistry()
        val key = key("healthy")
        val holder = holder("healthy", Instant.EPOCH)
        registry.put(key, holder)

        assertSame(holder, registry.getReusable(key))
        assertSame(holder, registry.lookupByToken("token-healthy"))
    }

    @Test
    fun `terminal session is removed instead of reused`() {
        val registry = SabrSessionRegistry()
        val key = key("terminal")
        val holder = holder("terminal", Instant.EPOCH)
        registry.put(key, holder)
        holder.failTerminal("upstream unauthorized")

        assertNull(registry.getReusable(key))
        assertNull(registry.lookupByToken("token-terminal"))
    }

    @Test
    fun `network failed session is removed instead of reused`() {
        val registry = SabrSessionRegistry()
        val key = key("network")
        val holder = holder("network", Instant.EPOCH)
        registry.put(key, holder)
        holder.recordNetworkFailure("connection reset")

        assertNull(registry.getReusable(key))
        assertNull(registry.lookupByToken("token-network"))
    }

    @Test
    fun `duplicate registration keeps original and closes candidate`() {
        val registry = SabrSessionRegistry()
        val key = key("same")
        val original = holder("same", Instant.EPOCH, "original-token")
        val candidate = holder("same", Instant.EPOCH.plusSeconds(1), "candidate-token")

        registry.put(key, original)
        val active = registry.put(key, candidate)

        assertSame(original, active)
        assertSame(original, registry.get(key))
        assertSame(original, registry.lookupByToken(original.sessionToken))
        assertNull(registry.lookupByToken(candidate.sessionToken))
        verify(exactly = 1) { candidate.session.clearCache() }
        verify(exactly = 0) { original.session.clearCache() }
    }

    @Test
    fun `concurrent duplicate registrations retain one session and close all losers`() {
        val registry = SabrSessionRegistry()
        val contenders = (0 until 16).map {
            holder("same", Instant.EPOCH.plusMillis(it.toLong()), "token-$it")
        }
        val ready = CountDownLatch(contenders.size)
        val start = CountDownLatch(1)
        val threads = contenders.map { contender ->
            Thread {
                ready.countDown()
                start.await()
                registry.put(contender.key, contender)
            }.apply { start() }
        }

        ready.await()
        start.countDown()
        threads.forEach(Thread::join)

        val active = registry.get(key("same"))
        assertTrue(contenders.any { it === active })
        contenders.forEach { contender ->
            verify(exactly = if (contender === active) 0 else 1) { contender.session.clearCache() }
            if (contender === active) {
                assertSame(active, registry.lookupByToken(contender.sessionToken))
            } else {
                assertNull(registry.lookupByToken(contender.sessionToken))
            }
        }
    }

    @Test
    fun `removal closes extractor session`() {
        val registry = SabrSessionRegistry()
        val holder = holder("removed", Instant.EPOCH)
        registry.put(holder.key, holder)

        registry.remove(holder)

        assertNull(registry.get(holder.key))
        verify(exactly = 1) { holder.session.clearCache() }
    }

    private fun key(id: String): SabrSessionKey = SabrSessionKey(id, "user", 140, null, 137, 0L)

    private fun holder(
        videoId: String,
        lastRequestAt: Instant,
        sessionToken: String = "token-$videoId",
    ): SabrSessionHolder {
        val key = key(videoId)
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns state
        every { session.clearCache() } returns Unit
        every { state.setActiveTrackTypes(true, true) } returns Unit
        return SabrSessionHolder(
            session = session,
            info = info(videoId),
            audioFormat = format(140, isAudio = true),
            videoFormat = format(137, isAudio = false),
            sessionToken = sessionToken,
            key = key,
            lastRequestAt = lastRequestAt,
        )
    }

    private fun info(videoId: String): YoutubeSabrInfo {
        val info = mockk<YoutubeSabrInfo>()
        every { info.videoId } returns videoId
        return info
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        return format
    }
}
