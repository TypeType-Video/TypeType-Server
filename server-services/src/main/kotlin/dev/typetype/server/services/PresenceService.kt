package dev.typetype.server.services

import dev.typetype.server.models.ActiveSessionNowPlayingItem
import dev.typetype.server.models.SessionPlaybackProgressRequest
import dev.typetype.server.models.SessionPlaybackStartRequest
import java.util.concurrent.ConcurrentHashMap

class PresenceService(
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val hasActiveKey: suspend (String) -> Boolean = { false },
) {
    private val nowPlaying = ConcurrentHashMap<String, ActiveSessionNowPlayingItem>()

    suspend fun reportPlaybackStart(userId: String, request: SessionPlaybackStartRequest): Unit =
        record(userId, ActiveSessionNowPlayingMapper.fromStart(request, nowProvider()))

    suspend fun reportPlaybackProgress(userId: String, request: SessionPlaybackProgressRequest): Unit {
        val current = nowPlaying[userId]
        record(userId, ActiveSessionNowPlayingMapper.fromProgress(current, request, nowProvider()))
    }

    fun reportPlaybackStop(userId: String): Unit {
        nowPlaying.remove(userId)
    }

    suspend fun current(userId: String): ActiveSessionNowPlayingItem? {
        val now = nowProvider()
        pruneExpired(now)
        return nowPlaying[userId]?.takeIf { now - it.updatedAt <= ACTIVITY_TTL_MS }
    }

    private suspend fun record(userId: String, value: ActiveSessionNowPlayingItem?) {
        if (value == null) return
        if (!hasActiveKey(userId)) {
            nowPlaying.remove(userId)
            return
        }
        nowPlaying[userId] = value
    }

    private fun pruneExpired(now: Long): Unit {
        nowPlaying.entries.removeIf { now - it.value.updatedAt > ACTIVITY_TTL_MS }
    }

    companion object {
        const val ACTIVITY_TTL_MS = 120_000L
    }
}
