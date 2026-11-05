package dev.typetype.server.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class BilibiliMirrorProber(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    private val lastProbe = ConcurrentHashMap<String, Long>()

    fun probe(urls: List<String>, totalLength: Long?) {
        urls.distinctBy { it.toHttpUrlOrNull()?.host }.forEach { url ->
            val host = url.toHttpUrlOrNull()?.host ?: return@forEach
            val now = nowMs()
            var claimed = false
            lastProbe.compute(host) { _, previous ->
                if (previous != null && now - previous < PROBE_INTERVAL_MS) previous else now.also { claimed = true }
            }
            if (claimed) scope.launch { runInterruptible(Dispatchers.IO) { measure(url, totalLength) } }
        }
    }

    // A range in the middle of the file is rarely in the CDN edge cache, so it measures the real path.
    private fun measure(url: String, totalLength: Long?) {
        val start = if (totalLength != null && totalLength > PROBE_BYTES * 8) {
            random.nextLong(totalLength / 4, totalLength / 2)
        } else {
            0L
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", OkHttpProxyService.BILIBILI_USER_AGENT)
            .header("Referer", "https://www.bilibili.com")
            .header("Accept", "*/*")
            .header("Range", "bytes=$start-${start + PROBE_BYTES - 1}")
            .build()
        val started = System.nanoTime()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code != 206) {
                    if (!response.isSuccessful) BilibiliMirrorSpeedTracker.recordFailure(url)
                    return
                }
                val opened = System.nanoTime()
                BilibiliMirrorSpeedTracker.recordSuccess(url, (opened - started) / 1_000_000)
                val bytes = response.body.source().readByteArray().size
                val seconds = (System.nanoTime() - opened) / 1e9
                if (bytes >= MIN_MEASURED_BYTES && seconds > 0) {
                    BilibiliMirrorSpeedTracker.recordThroughput(url, bytes / seconds)
                }
            }
        } catch (_: IOException) {
            BilibiliMirrorSpeedTracker.recordFailure(url)
        }
    }

    companion object {
        const val PROBE_INTERVAL_MS = 180_000L
        const val PROBE_BYTES = 128L * 1024
        const val MIN_MEASURED_BYTES = 64 * 1024
    }
}
