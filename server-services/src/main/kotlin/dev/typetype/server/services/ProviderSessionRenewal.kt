package dev.typetype.server.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.ArrayDeque

/**
 * A session the provider answers "sign in to confirm you're not a bot" to keeps being refused
 * for more than ten minutes. After a few refusals close together a new session is asked for,
 * once, then none for a while: a new session is not always answered either, and must not be
 * renewed in a loop. YOUTUBE_SESSION_RENEWAL=off turns this off.
 */
class ProviderSessionRenewal(
    private val enabled: Boolean = System.getenv("YOUTUBE_SESSION_RENEWAL")?.lowercase() != "off",
    private val refusals: Int = 3,
    private val withinMs: Long = 60_000L,
    private val restMs: Long = 120_000L,
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val renew: suspend (videoUrl: String) -> Boolean,
) {
    private val recent = ArrayDeque<Long>()
    private var renewedAt = Long.MIN_VALUE / 2

    fun refused(videoUrl: String) {
        if (!enabled || !due()) return
        scope.launch {
            val done = runCatching { renew(videoUrl) }.getOrDefault(false)
            logger.warn("youtube_session event=renewed after={} refusals done={}", refusals, done)
        }
    }

    @Synchronized
    private fun due(): Boolean {
        val time = now()
        if (time - renewedAt < restMs) return false
        recent.addLast(time)
        while (recent.isNotEmpty() && time - recent.first() > withinMs) recent.removeFirst()
        if (recent.size < refusals) return false
        recent.clear()
        renewedAt = time
        return true
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ProviderSessionRenewal::class.java)
    }
}
