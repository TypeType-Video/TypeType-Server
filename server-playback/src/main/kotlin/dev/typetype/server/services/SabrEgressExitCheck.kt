package dev.typetype.server.services

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo

/**
 * Judges an exit the way a viewer meets it: a playback session opened in the middle of the video.
 * YouTube serves the start of a video even to a session it keeps on hold, so only a start away
 * from zero shows whether the exit is accepted (stream protection status 1) or not (2 and above).
 */
class SabrEgressExitCheck(
    private val sessionStore: SabrSessionStore,
    private val playback: SabrPlaybackSessionService = SabrPlaybackSessionService(sessionStore),
    private val waitMs: Long = 10_000L,
    private val pollMs: Long = 250L,
) : EgressExitCheck {
    override suspend fun renew(videoId: String) = sessionStore.renewTokenSession(videoId)

    override suspend fun accepts(videoId: String): Boolean {
        val prepared = sessionStore.fetchInfo(videoId, PROBE_START_MS) ?: return false
        val audio = prepared.info.findBestAudioFormat() ?: return false
        val video = prepared.info.lightestVideo() ?: return false
        val holder = playback.prepare(videoId, PROBE_USER, prepared, audio, video, PROBE_START_MS).holder
        return try {
            withTimeoutOrNull(waitMs) { awaitVerdict(holder) } ?: false
        } finally {
            sessionStore.release(holder)
        }
    }

    private suspend fun awaitVerdict(holder: SabrSessionHolder): Boolean {
        while (true) {
            if (holder.terminalFailure() != null) return false
            val status = holder.session.maxStreamProtectionStatus
            if (status >= PENDING_STATUS) return false
            if (status == ACCEPTED_STATUS && holder.session.requestNumber > 0) return true
            delay(pollMs)
        }
    }

    private fun YoutubeSabrInfo.lightestVideo(): YoutubeSabrFormat? =
        formats.filter { it.isVideo && it.mimeType.orEmpty().contains("avc1") }
            .minWithOrNull(compareBy<YoutubeSabrFormat> { it.height }.thenBy { it.bitrate })

    companion object {
        const val PROBE_USER = "egress-exit-check"
        const val PENDING_STATUS = 2
        private const val ACCEPTED_STATUS = 1
        private const val PROBE_START_MS = 120_000L
    }
}
