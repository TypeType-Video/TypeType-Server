package dev.typetype.server.routes

import java.util.LinkedHashMap

internal data class SabrPlaybackPrewarmKey(
    val userId: String,
    val videoId: String,
    val videoItag: Int,
    val audioItag: Int,
    val audioTrackId: String?,
    val startTimeMs: Long,
    val audioOnly: Boolean,
    val isLive: Boolean,
)

internal data class SabrPlaybackPrewarmSession(
    val sessionToken: String,
    val startTimeMs: Long,
    val ready: Boolean,
)

internal class SabrPlaybackPrewarmHandoffs(
    private val maxEntries: Int = MAX_ENTRIES,
    private val ttlNanos: Long = TTL_NANOS,
) {
    private data class Entry(val session: SabrPlaybackPrewarmSession, val expiresAtNanos: Long)

    private val entries = LinkedHashMap<SabrPlaybackPrewarmKey, Entry>()

    fun key(
        userId: String?,
        videoId: String,
        request: SabrPlaybackRequest,
        startTimeMs: Long,
    ): SabrPlaybackPrewarmKey? {
        val scope = userId?.takeIf { it.isNotBlank() } ?: return null
        val videoItag = request.videoItag ?: return null
        val audioItag = request.audioItag ?: return null
        return SabrPlaybackPrewarmKey(
            userId = scope,
            videoId = videoId,
            videoItag = videoItag,
            audioItag = audioItag,
            audioTrackId = request.audioTrackId,
            startTimeMs = startTimeMs,
            audioOnly = request.audioOnly,
            isLive = request.isLive,
        )
    }

    @Synchronized
    fun remember(key: SabrPlaybackPrewarmKey, session: SabrPlaybackPrewarmSession) {
        val now = System.nanoTime()
        evictExpired(now)
        entries[key] = Entry(session, now + ttlNanos)
        while (entries.size > maxEntries) {
            entries.remove(entries.keys.first())
        }
    }

    @Synchronized
    fun take(key: SabrPlaybackPrewarmKey): SabrPlaybackPrewarmSession? {
        val now = System.nanoTime()
        val entry = entries.remove(key) ?: return null
        return entry.session.takeUnless { now - entry.expiresAtNanos >= 0L }
    }

    private fun evictExpired(now: Long) {
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().expiresAtNanos >= 0L) iterator.remove()
        }
    }

    private companion object {
        const val MAX_ENTRIES = 64
        const val TTL_NANOS = 60_000_000_000L
    }
}
