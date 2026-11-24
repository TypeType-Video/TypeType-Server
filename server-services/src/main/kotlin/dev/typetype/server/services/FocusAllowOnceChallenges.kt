package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

internal class FocusAllowOnceChallenges(
    private val cache: CacheService? = null,
) {
    private val localChallenges = ConcurrentHashMap<String, FocusAllowOnceChallengePayload>()

    suspend fun save(challengeId: String, payload: FocusAllowOnceChallengePayload, now: Long) {
        if (cache != null) {
            val serialized = CacheJson.encodeToString(FocusAllowOnceChallengePayload.serializer(), payload)
            cache.set("$CACHE_PREFIX$challengeId", serialized, CHALLENGE_TTL_SECONDS)
            return
        }
        if (localChallenges.size >= MAX_LOCAL_CHALLENGES) {
            localChallenges.entries.removeIf { it.value.expiresAt < now }
        }
        localChallenges[challengeId] = payload
    }

    suspend fun take(challengeId: String): FocusAllowOnceChallengePayload? {
        if (cache == null) return localChallenges.remove(challengeId)
        val raw = cache.getAndDelete("$CACHE_PREFIX$challengeId") ?: return null
        return runCatching { CacheJson.decodeFromString(FocusAllowOnceChallengePayload.serializer(), raw) }.getOrNull()
    }

    companion object {
        private const val CHALLENGE_TTL_SECONDS = 300L
        private const val MAX_LOCAL_CHALLENGES = 1000
        private const val CACHE_PREFIX = "focus:allow-once:challenge:"

        fun normalizeVideoKey(rawUrl: String): String? {
            val trimmed = rawUrl.trim()
            if (trimmed.isBlank()) return null
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase()
            if ((scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) return null
            return normalizeBlockedVideoKey(trimmed).takeIf { it.isNotBlank() }
        }

        fun currentDayKey(now: Long, rowTimezone: String?, requestedTimezone: String?): String {
            val zone = resolveZone(rowTimezone, requestedTimezone)
            return LocalDate.ofInstant(Instant.ofEpochMilli(now), zone).toString()
        }

        private fun resolveZone(rowTimezone: String?, requestedTimezone: String?): ZoneId =
            requestedTimezone?.trim()?.takeIf { it.isNotEmpty() }?.let(::parseZone)
                ?: rowTimezone?.trim()?.takeIf { it.isNotEmpty() }?.let(::parseZone)
                ?: ZoneOffset.UTC

        private fun parseZone(id: String): ZoneId? = runCatching { ZoneId.of(id) }.getOrNull()
    }
}
