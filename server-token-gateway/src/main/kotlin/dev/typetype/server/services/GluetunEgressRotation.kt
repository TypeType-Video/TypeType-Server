package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.ArrayDeque

/**
 * Moves the YouTube egress to another VPN server through the gluetun control server once the
 * provider starts refusing the current exit: YouTube flags an exit IP over time, then answers bot
 * checks and SABR attestation demands that a fresh exit does not get. Every viewer shares the exit
 * and a reconnect cuts running streams, so it rotates at most once per [restMs].
 */
class GluetunEgressRotation(
    private val controlUrl: String,
    private val apiKey: String,
    private val refusals: Int = 2,
    private val withinMs: Long = 120_000L,
    private val restMs: Long = 600_000L,
    private val now: () -> Long = System::currentTimeMillis,
    private val settle: suspend () -> Unit = { delay(SETTLE_MS) },
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(10)).build(),
) {
    private val mutex = Mutex()
    private val recent = ArrayDeque<Long>()
    private var rotatedAt = Long.MIN_VALUE / 2

    /** Counts a refusal and rotates once [refusals] of them land within [withinMs]. */
    suspend fun refused(reason: String): Boolean = if (count()) rotate(reason) else false

    suspend fun rotate(reason: String): Boolean = mutex.withLock {
        if (now() - rotatedAt < restMs) return@withLock false
        rotatedAt = now()
        val done = withContext(Dispatchers.IO) { put("stopped") && put("running") }
        if (done) settle()
        logger.warn("youtube_egress event=rotated reason={} done={}", reason, done)
        done
    }

    @Synchronized
    private fun count(): Boolean {
        val time = now()
        recent.addLast(time)
        while (recent.isNotEmpty() && time - recent.first() > withinMs) recent.removeFirst()
        if (recent.size < refusals) return false
        recent.clear()
        return true
    }

    private fun put(status: String): Boolean = runCatching {
        val request = Request.Builder()
            .url("${controlUrl.trimEnd('/')}/v1/vpn/status")
            .header("X-API-Key", apiKey)
            .put("""{"status":"$status"}""".toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    companion object {
        private const val SETTLE_MS = 2_000L
        private val JSON = "application/json".toMediaType()
        private val logger = LoggerFactory.getLogger(GluetunEgressRotation::class.java)

        fun fromEnvironment(env: (String) -> String? = System::getenv): GluetunEgressRotation? {
            val url = SecretConfigReader.read("YOUTUBE_EGRESS_GLUETUN_URL", env) ?: return null
            val key = SecretConfigReader.read("YOUTUBE_EGRESS_GLUETUN_API_KEY", env) ?: return null
            return GluetunEgressRotation(url, key)
        }
    }
}
