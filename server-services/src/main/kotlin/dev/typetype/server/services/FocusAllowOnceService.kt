package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.FocusAllowOnceGrantsTable
import dev.typetype.server.models.FocusAllowOnceChallengeResponse
import dev.typetype.server.models.FocusAllowOnceVerifyResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class FocusAllowOnceService(
    private val clock: () -> Long = System::currentTimeMillis,
    private val cache: CacheService? = null,
    private val generateCaptchaCode: () -> String = FocusCaptchaGenerator::generateCode,
) {
    private val localChallenges = ConcurrentHashMap<String, FocusAllowOnceChallengePayload>()

    internal fun remainingTodayInTx(
        userId: String,
        now: Long,
        rowTimezone: String?,
        requestedTimezone: String? = null,
    ): Int {
        val dayKey = currentDayKey(now, resolveZone(rowTimezone, requestedTimezone))
        val used = countUsedInDayTx(userId, dayKey)
        return (DAILY_LIMIT - used).coerceAtLeast(0)
    }

    internal fun activeVideoKeysInTx(userId: String, now: Long): Set<String> =
        FocusAllowOnceGrantsTable.select(FocusAllowOnceGrantsTable.videoKey)
            .where {
                (FocusAllowOnceGrantsTable.userId eq userId) and
                    (FocusAllowOnceGrantsTable.expiresAt greater now)
            }
            .mapTo(HashSet()) { it[FocusAllowOnceGrantsTable.videoKey] }

    suspend fun createChallenge(
        userId: String,
        rawVideoUrl: String,
        requestedTimezone: String? = null,
    ): FocusAllowOnceChallengeResult {
        val videoKey = normalizeAllowOnceVideoKey(rawVideoUrl) ?: return FocusAllowOnceChallengeResult.InvalidVideo
        val now = clock()
        val remainingToday = DatabaseFactory.query {
            val row = readSettledFocusRow(userId, now)
            if (!isActiveFocusRow(row, now) || !row.strictBlocking) return@query null
            remainingTodayInTx(userId, now, row.timezone, requestedTimezone)
        } ?: return FocusAllowOnceChallengeResult.NotEligible
        if (remainingToday <= 0) return FocusAllowOnceChallengeResult.Exhausted

        val code = generateCaptchaCode()
        val challengeId = UUID.randomUUID().toString()
        val expiresAt = now + CHALLENGE_TTL_MS
        saveChallenge(challengeId, FocusAllowOnceChallengePayload(userId, videoKey, code, expiresAt), now)
        return FocusAllowOnceChallengeResult.Success(
            FocusAllowOnceChallengeResponse(
                challengeId = challengeId,
                challengeText = code,
                expiresAt = expiresAt,
                remainingToday = remainingToday,
                dailyLimit = DAILY_LIMIT,
            ),
        )
    }

    suspend fun verifyChallenge(
        userId: String,
        challengeId: String,
        rawVideoUrl: String,
        rawCode: String,
        requestedTimezone: String? = null,
    ): FocusAllowOnceVerifyResult {
        val videoKey = normalizeAllowOnceVideoKey(rawVideoUrl) ?: return FocusAllowOnceVerifyResult.InvalidVideo
        val trimmedCode = rawCode.trim()
        if (challengeId.isBlank() || trimmedCode.isBlank()) return FocusAllowOnceVerifyResult.InvalidChallenge
        val now = clock()
        val payload = takeChallenge(challengeId)
        val valid = payload != null &&
            payload.userId == userId &&
            payload.videoKey == videoKey &&
            now <= payload.expiresAt &&
            trimmedCode == payload.code
        if (!valid) return FocusAllowOnceVerifyResult.InvalidChallenge

        return DatabaseFactory.query {
            SubscriptionMutationLock.acquire(userId)
            val row = readSettledFocusRow(userId, now)
            if (!isActiveFocusRow(row, now) || !row.strictBlocking) return@query FocusAllowOnceVerifyResult.NotEligible
            val dayKey = currentDayKey(now, resolveZone(row.timezone, requestedTimezone))
            val usedToday = countUsedInDayTx(userId, dayKey)
            val existingExpiry = activeExpiryInTx(userId, videoKey, now)
            if (existingExpiry != null) {
                return@query FocusAllowOnceVerifyResult.Success(
                    FocusAllowOnceVerifyResponse(
                        remainingToday = (DAILY_LIMIT - usedToday).coerceAtLeast(0),
                        dailyLimit = DAILY_LIMIT,
                        expiresAt = existingExpiry,
                    ),
                )
            }
            if (usedToday >= DAILY_LIMIT) return@query FocusAllowOnceVerifyResult.Exhausted
            val expiresAt = now + ALLOW_ONCE_DURATION_MS
            FocusAllowOnceGrantsTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[FocusAllowOnceGrantsTable.userId] = userId
                it[FocusAllowOnceGrantsTable.videoKey] = videoKey
                it[FocusAllowOnceGrantsTable.dayKey] = dayKey
                it[grantedAt] = now
                it[FocusAllowOnceGrantsTable.expiresAt] = expiresAt
            }
            FocusAllowOnceVerifyResult.Success(
                FocusAllowOnceVerifyResponse(
                    remainingToday = (DAILY_LIMIT - (usedToday + 1)).coerceAtLeast(0),
                    dailyLimit = DAILY_LIMIT,
                    expiresAt = expiresAt,
                ),
            )
        }
    }

    private fun countUsedInDayTx(userId: String, dayKey: String): Int =
        FocusAllowOnceGrantsTable.selectAll()
            .where { (FocusAllowOnceGrantsTable.userId eq userId) and (FocusAllowOnceGrantsTable.dayKey eq dayKey) }
            .count()
            .toInt()

    private fun activeExpiryInTx(userId: String, videoKey: String, now: Long): Long? =
        FocusAllowOnceGrantsTable.select(FocusAllowOnceGrantsTable.expiresAt)
            .where {
                (FocusAllowOnceGrantsTable.userId eq userId) and
                    (FocusAllowOnceGrantsTable.videoKey eq videoKey) and
                    (FocusAllowOnceGrantsTable.expiresAt greater now)
            }
            .maxOfOrNull { it[FocusAllowOnceGrantsTable.expiresAt] }

    private suspend fun saveChallenge(challengeId: String, payload: FocusAllowOnceChallengePayload, now: Long) {
        if (cache != null) {
            val serialized = CacheJson.encodeToString(FocusAllowOnceChallengePayload.serializer(), payload)
            cache.set("$CHALLENGE_CACHE_PREFIX$challengeId", serialized, CHALLENGE_TTL_SECONDS)
            return
        }
        if (localChallenges.size >= MAX_LOCAL_CHALLENGES) {
            localChallenges.entries.removeIf { it.value.expiresAt < now }
        }
        localChallenges[challengeId] = payload
    }

    private suspend fun takeChallenge(challengeId: String): FocusAllowOnceChallengePayload? {
        if (cache == null) return localChallenges.remove(challengeId)
        val raw = cache.getAndDelete("$CHALLENGE_CACHE_PREFIX$challengeId") ?: return null
        return runCatching { CacheJson.decodeFromString(FocusAllowOnceChallengePayload.serializer(), raw) }.getOrNull()
    }

    private fun normalizeAllowOnceVideoKey(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if ((scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) return null
        return normalizeBlockedVideoKey(trimmed).takeIf { it.isNotBlank() }
    }

    private fun resolveZone(rowTimezone: String?, requestedTimezone: String?): ZoneId =
        requestedTimezone?.trim()?.takeIf { it.isNotEmpty() }?.let(::parseZone)
            ?: rowTimezone?.trim()?.takeIf { it.isNotEmpty() }?.let(::parseZone)
            ?: ZoneOffset.UTC

    private fun parseZone(id: String): ZoneId? = runCatching { ZoneId.of(id) }.getOrNull()

    private fun currentDayKey(now: Long, zone: ZoneId): String =
        LocalDate.ofInstant(Instant.ofEpochMilli(now), zone).toString()

    companion object {
        const val DAILY_LIMIT = 3
        const val ALLOW_ONCE_DURATION_MS = 2 * 60 * 60 * 1000L
        const val CHALLENGE_TTL_MS = 5 * 60 * 1000L
        private const val CHALLENGE_TTL_SECONDS = 300L
        private const val MAX_LOCAL_CHALLENGES = 1000
        private const val CHALLENGE_CACHE_PREFIX = "focus:allow-once:challenge:"
    }
}
