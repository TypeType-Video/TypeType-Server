package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.ArrayDeque

/**
 * Moves the YouTube egress to another VPN server through the gluetun control server once the
 * provider starts refusing the current exit: YouTube rates each exit IP, then answers bot checks and
 * SABR attestation demands that a well rated exit does not get. Every viewer shares the exit and a
 * reconnect cuts running streams, so it rotates at most once per [restMs].
 *
 * A new exit only stays when [exitCheck] accepts it, with a token session minted on it. With named
 * [servers] the rotation knows where it is: it skips the exits refused lately and, when none of the
 * candidates passes, goes back to the last exit that did.
 */
class GluetunEgressRotation(
    private val control: GluetunControl,
    private val servers: List<String> = emptyList(),
    private val refusals: Int = 2,
    private val withinMs: Long = 120_000L,
    private val restMs: Long = 600_000L,
    private val attempts: Int = 3,
    private val refusedForMs: Long = 3_600_000L,
    private val now: () -> Long = System::currentTimeMillis,
    private val settle: suspend () -> Unit = { delay(SETTLE_MS) },
    private val pick: (List<String>) -> String = { it.random() },
) {
    @Volatile
    var exitCheck: EgressExitCheck? = null

    private val mutex = Mutex()
    private val recent = ArrayDeque<Long>()
    private val refusedAt = HashMap<String, Long>()
    private var rotatedAt = Long.MIN_VALUE / 2
    private var current: String? = null
    private var lastAccepted: String? = null

    /** Counts a refusal and rotates once [refusals] of them land within [withinMs]. */
    suspend fun refused(reason: String, videoId: String? = null): Boolean =
        if (count()) rotate(reason, videoId) else false

    suspend fun rotate(reason: String, videoId: String? = null): Boolean = mutex.withLock {
        if (now() - rotatedAt < restMs) return@withLock false
        rotatedAt = now()
        current?.let { refusedAt[it] = now() }
        repeat(attempts) { attempt ->
            val candidate = candidate()
            if (!move(candidate)) {
                logger.warn("youtube_egress event=rotated reason={} done=false", reason)
                return@withLock false
            }
            val accepted = accepted(videoId)
            logger.warn(
                "youtube_egress event=rotated reason={} attempt={} server={} accepted={}",
                reason, attempt + 1, candidate ?: "random", accepted,
            )
            current = candidate
            if (accepted) {
                lastAccepted = candidate
                return@withLock true
            }
            candidate?.let { refusedAt[it] = now() }
        }
        goBack(videoId)
        false
    }

    private suspend fun goBack(videoId: String?) {
        val server = lastAccepted?.takeIf { it != current } ?: return
        if (!move(server)) return
        current = server
        if (videoId != null) exitCheck?.renew(videoId)
        logger.warn("youtube_egress event=returned server={}", server)
    }

    private suspend fun move(server: String?): Boolean {
        val moved = withContext(Dispatchers.IO) { if (server == null) control.reconnect() else control.connectTo(server) }
        if (moved) settle()
        return moved
    }

    private suspend fun accepted(videoId: String?): Boolean {
        val check = exitCheck ?: return true
        if (videoId == null) return true
        check.renew(videoId)
        return check.accepts(videoId)
    }

    private fun candidate(): String? {
        if (servers.isEmpty()) return null
        val others = servers.filter { it != current }.ifEmpty { servers }
        val fresh = others.filter { now() - (refusedAt[it] ?: Long.MIN_VALUE / 2) >= refusedForMs }
        return if (fresh.isNotEmpty()) pick(fresh) else others.minBy { refusedAt[it] ?: 0L }
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

    companion object {
        private const val SETTLE_MS = 3_000L
        private val logger = LoggerFactory.getLogger(GluetunEgressRotation::class.java)

        fun fromEnvironment(env: (String) -> String? = System::getenv): GluetunEgressRotation? {
            val url = SecretConfigReader.read("YOUTUBE_EGRESS_GLUETUN_URL", env) ?: return null
            val key = SecretConfigReader.read("YOUTUBE_EGRESS_GLUETUN_API_KEY", env) ?: return null
            val servers = env("YOUTUBE_EGRESS_GLUETUN_SERVERS").orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
            return GluetunEgressRotation(GluetunControl(url, key), servers)
        }
    }
}
