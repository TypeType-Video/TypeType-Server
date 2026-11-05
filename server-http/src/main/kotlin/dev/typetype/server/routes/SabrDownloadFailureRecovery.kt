package dev.typetype.server.routes

import dev.typetype.server.services.SabrDownloadRange
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import org.slf4j.LoggerFactory

internal class SabrDownloadFailureRecovery(private val store: SabrSessionStore) {
    suspend fun recover(holder: SabrSessionHolder, range: SabrDownloadRange, error: Exception) {
        val reason = error.message.orEmpty()
        val protected = PROTECTED_MARKERS.any { reason.contains(it, ignoreCase = true) }
        logger.warn(
            "sabr_download_failed videoId={} part={}/{} protected={} reason={}",
            holder.key.videoId,
            range.part + 1,
            range.parts,
            protected,
            reason.take(MAX_REASON_LENGTH),
        )
        runCatching {
            if (protected) store.recoverProtectedPlaybackInfo(holder) else store.invalidatePlaybackInfo(holder.key.videoId)
        }.onFailure { logger.warn("sabr_download_recovery_failed videoId={} error={}", holder.key.videoId, it.message) }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(SabrDownloadFailureRecovery::class.java)
        val PROTECTED_MARKERS = listOf("attestation required", "attestation remained pending", "protected no-media")
        const val MAX_REASON_LENGTH = 200
    }
}
