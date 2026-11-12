package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Checks every new token session with the last video played, before a viewer meets it: a session
 * YouTube keeps on hold only shows when someone resumes or seeks, a minute or two too late for them.
 */
class TokenSessionWatch(
    private val generation: suspend (videoId: String) -> String?,
    private val check: EgressExitCheck,
    private val acceptance: TokenSessionAcceptance,
    private val periodMs: Long = 60_000L,
) {
    @Volatile
    private var videoId: String? = null
    private var checked: String? = null

    fun played(videoId: String) {
        this.videoId = videoId
    }

    suspend fun run() {
        while (true) {
            delay(periodMs)
            try {
                once()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
    }

    suspend fun once() {
        val video = videoId ?: return
        val current = generation(video) ?: return
        if (current == checked) return
        checked = current
        if (!check.accepts(video)) acceptance.onHold("session_check", video)
    }
}
