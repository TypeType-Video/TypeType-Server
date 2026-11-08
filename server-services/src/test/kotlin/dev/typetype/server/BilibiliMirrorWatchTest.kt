package dev.typetype.server

import dev.typetype.server.services.BilibiliMirrorWatch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class BilibiliMirrorWatchTest {
    private var now = 1_000_000_000L
    private val probes = mutableListOf<List<String>>()
    private var fetches = 0
    private val valid = "https://upos-sz-mirrorcosov.bilivideo.com/v.m4s?deadline=${1_000_000_000L / 1000 + 3_600}"
    private val expired = "https://upos-sz-mirrorcosov.bilivideo.com/v.m4s?deadline=${1_000_000_000L / 1000 + 60}"

    private fun watch(cache: FakeCacheService = FakeCacheService(), sample: BilibiliMirrorWatch.Sample? = null) =
        BilibiliMirrorWatch(
            probe = { urls, _ -> probes += urls },
            cache = cache,
            fetchSample = { fetches++; sample },
            nowMs = { now },
            random = Random(0),
        )

    @Test
    fun `probes the last played media often while Bilibili is in use, pauses when idle`() = runBlocking {
        val watch = watch()
        watch.remember(listOf(valid), 40_000_000)

        watch.tick()
        now += BilibiliMirrorWatch.ACTIVE_PROBE_INTERVAL_MS - 1
        watch.tick()
        now += BilibiliMirrorWatch.ACTIVE_PROBE_JITTER_MS + 1
        watch.tick()
        assertEquals(2, probes.size)

        now += BilibiliMirrorWatch.ACTIVE_WINDOW_MS
        watch.tick()
        now += BilibiliMirrorWatch.ACTIVE_PROBE_INTERVAL_MS
        watch.tick()
        assertEquals(2, probes.size)
        assertEquals(0, fetches)
    }

    @Test
    fun `does not probe or fetch sample when nothing was played`() = runBlocking {
        val watch = watch(sample = BilibiliMirrorWatch.Sample(listOf(valid), null))

        watch.tick()
        assertEquals(0, probes.size)
        assertEquals(0, fetches)
    }

    @Test
    fun `finds its own sample when played media expired and active window is current`() = runBlocking {
        val watch = watch(sample = BilibiliMirrorWatch.Sample(listOf(valid), null))
        watch.remember(listOf(expired), null)

        watch.tick()
        assertEquals(listOf(listOf(valid)), probes)
        assertEquals(1, fetches)
    }

    @Test
    fun `does not keep asking for a sample when none can be found`() = runBlocking {
        val watch = watch(sample = null)
        watch.remember(listOf(expired), null)

        watch.tick()
        assertEquals(1, fetches)

        now += BilibiliMirrorWatch.ACTIVE_PROBE_INTERVAL_MS
        watch.remember(listOf(expired), null)
        watch.tick()
        assertEquals(1, fetches)

        now += BilibiliMirrorWatch.FETCH_INTERVAL_MS
        watch.remember(listOf(expired), null)
        watch.tick()
        assertEquals(2, fetches)
        assertEquals(0, probes.size)
    }

    @Test
    fun `ignores links about to expire`() = runBlocking {
        val watch = watch(sample = null)
        watch.remember(listOf(expired), null)

        watch.tick()
        assertEquals(0, probes.size)
    }
}
