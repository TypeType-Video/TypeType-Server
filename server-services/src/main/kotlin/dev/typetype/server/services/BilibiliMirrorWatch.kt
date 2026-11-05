package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class BilibiliMirrorWatch(
    private val probe: (urls: List<String>, totalLength: Long?) -> Unit,
    private val cache: CacheService,
    private val fetchSample: suspend () -> Sample?,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    class Sample(val urls: List<String>, val totalLength: Long?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile
    private var sample: Sample? = null

    @Volatile
    private var lastUseMs = 0L

    @Volatile
    private var lastProbeMs = 0L

    @Volatile
    private var lastFetchMs = 0L

    fun remember(urls: List<String>, totalLength: Long?) {
        lastUseMs = nowMs()
        val fresh = Sample(urls, totalLength)
        if (!fresh.validAt(nowMs())) return
        sample = fresh
        scope.launch { runCatching { cache.set(CACHE_KEY, encode(fresh), fresh.secondsLeft(nowMs())) } }
    }

    fun start(enabled: suspend () -> Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            delay(INITIAL_DELAY_MS)
            while (isActive) {
                try {
                    if (enabled()) tick()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    logger.warn("bilibili_mirror_watch event=tick_failed error={}", error.message)
                }
                delay(TICK_MS)
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    suspend fun tick() {
        val now = nowMs()
        val interval = if (now - lastUseMs < ACTIVE_WINDOW_MS) ACTIVE_PROBE_INTERVAL_MS else IDLE_PROBE_INTERVAL_MS
        if (now - lastProbeMs < interval) return
        val current = currentSample(now) ?: return
        lastProbeMs = now
        probe(current.urls, current.totalLength)
    }

    private suspend fun currentSample(now: Long): Sample? {
        sample?.takeIf { it.validAt(now) }?.let { return it }
        runCatching { cache.get(CACHE_KEY) }.getOrNull()?.let(::decode)?.takeIf { it.validAt(now) }?.let {
            sample = it
            return it
        }
        if (now - lastFetchMs < FETCH_INTERVAL_MS) return null
        lastFetchMs = now
        return fetchSample()?.takeIf { it.validAt(now) }?.also { sample = it }
    }

    private fun Sample.validAt(now: Long): Boolean = urls.isNotEmpty() && secondsLeft(now) > MIN_SECONDS_LEFT

    private fun Sample.secondsLeft(now: Long): Long {
        val deadline = urls.firstNotNullOfOrNull { DEADLINE.find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: return 0
        return deadline - now / 1000
    }

    private fun encode(sample: Sample): String = (listOf(sample.totalLength?.toString().orEmpty()) + sample.urls).joinToString("\n")

    private fun decode(value: String): Sample? {
        val lines = value.lines()
        if (lines.size < 2) return null
        return Sample(lines.drop(1).filter { it.isNotBlank() }, lines.first().toLongOrNull())
    }

    companion object {
        private const val CACHE_KEY = "bilibili-mirror-sample:v1"
        private const val INITIAL_DELAY_MS = 20_000L
        private const val TICK_MS = 60_000L
        const val ACTIVE_WINDOW_MS = 30 * 60_000L
        const val ACTIVE_PROBE_INTERVAL_MS = 3 * 60_000L
        const val IDLE_PROBE_INTERVAL_MS = 20 * 60_000L
        const val FETCH_INTERVAL_MS = 30 * 60_000L
        private const val MIN_SECONDS_LEFT = 120L
        private val DEADLINE = Regex("[?&]deadline=(\\d+)")
        private val logger = LoggerFactory.getLogger(BilibiliMirrorWatch::class.java)
    }
}
