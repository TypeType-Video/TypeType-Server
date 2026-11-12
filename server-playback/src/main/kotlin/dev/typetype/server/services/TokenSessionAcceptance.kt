package dev.typetype.server.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * YouTube rates the exit IP when a token session is minted and the verdict sticks to that session:
 * an accepted one plays anything, one put on hold only plays the start of a video. The rating of a
 * shared exit moves within minutes, so a session on hold is replaced by a new one, checked, until one
 * is accepted. Only when none is does the egress move to another exit.
 */
class TokenSessionAcceptance(
    private val check: EgressExitCheck,
    private val rotation: GluetunEgressRotation?,
    private val scope: CoroutineScope,
    private val tries: Int = 6,
    private val pauseMs: Long = 20_000L,
    private val restMs: Long = 120_000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val running = AtomicBoolean(false)

    @Volatile
    private var finishedAt = Long.MIN_VALUE / 2

    /** Starts one recovery; calls made while it runs or right after it are dropped. */
    fun onHold(reason: String, videoId: String): Boolean {
        if (now() - finishedAt < restMs || !running.compareAndSet(false, true)) return false
        scope.launch {
            try {
                recover(reason, videoId)
            } finally {
                finishedAt = now()
                running.set(false)
            }
        }
        return true
    }

    private suspend fun recover(reason: String, videoId: String) {
        repeat(tries) { attempt ->
            check.renew(videoId)
            val accepted = check.accepts(videoId)
            logger.warn("youtube_session event=replaced reason={} attempt={} accepted={}", reason, attempt + 1, accepted)
            if (accepted) return
            delay(pauseMs)
        }
        val rotated = rotation?.rotate(reason, videoId)
        logger.warn("youtube_session event=still_on_hold reason={} rotated={}", reason, rotated)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(TokenSessionAcceptance::class.java)
    }
}
