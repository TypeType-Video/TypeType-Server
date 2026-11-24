package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.models.FocusAllowOnceCancelResponse
import dev.typetype.server.models.FocusAllowOnceChallengeResponse
import dev.typetype.server.models.FocusAllowOnceVerifyResponse
import java.util.UUID

internal class FocusAllowOnceService(
    private val clock: () -> Long = System::currentTimeMillis,
    cache: CacheService? = null,
    private val generateCaptchaCode: () -> String = FocusCaptchaGenerator::generateCode,
) {
    private val challenges = FocusAllowOnceChallenges(cache)

    internal fun remainingTodayInTx(
        userId: String,
        now: Long,
        rowTimezone: String?,
        requestedTimezone: String? = null,
        kind: FocusAllowOnceKind = FocusAllowOnceKind.OUTSIDE_GROUP,
    ): Int {
        val dayKey = FocusAllowOnceChallenges.currentDayKey(now, rowTimezone, requestedTimezone)
        val used = FocusAllowOnceQueries.countUsedInDayTx(userId, dayKey, kind)
        return (kind.dailyLimit - used).coerceAtLeast(0)
    }

    internal fun activeVideoKeysInTx(userId: String, now: Long): Set<String> =
        FocusAllowOnceQueries.activeVideoKeysInTx(userId, now)

    internal fun grantAiApprovedInTx(
        userId: String,
        videoKey: String,
        rowTimezone: String?,
        requestedTimezone: String?,
        kind: FocusAllowOnceKind,
        now: Long,
    ): Long {
        val existing = FocusAllowOnceQueries.activeOrPendingGrantInTx(userId, videoKey, kind, now)
        if (existing != null && existing.unlockAt <= now) return existing.expiresAt
        val dayKey = FocusAllowOnceChallenges.currentDayKey(now, rowTimezone, requestedTimezone)
        val expiresAt = now + ALLOW_ONCE_DURATION_MS
        FocusAllowOnceQueries.insertGrantInTx(
            userId = userId,
            videoKey = videoKey,
            dayKey = dayKey,
            kind = kind,
            source = FocusAllowOnceQueries.SOURCE_AI_APPROVED,
            grantedAt = now,
            unlockAt = now,
            expiresAt = expiresAt,
        )
        return expiresAt
    }

    suspend fun createChallenge(
        userId: String,
        rawVideoUrl: String,
        requestedTimezone: String? = null,
        rawKind: String = FocusAllowOnceKind.OUTSIDE_GROUP.wireName,
    ): FocusAllowOnceChallengeResult {
        val kind = FocusAllowOnceKind.fromWire(rawKind) ?: return FocusAllowOnceChallengeResult.InvalidKind
        val videoKey = FocusAllowOnceChallenges.normalizeVideoKey(rawVideoUrl) ?: return FocusAllowOnceChallengeResult.InvalidVideo
        val now = clock()
        val state = DatabaseFactory.query {
            val row = readSettledFocusRow(userId, now)
            if (!isActiveFocusRow(row, now) || !row.strictBlocking) return@query null
            val remaining = remainingTodayInTx(userId, now, row.timezone, requestedTimezone, kind)
            val cooldownMs = FocusAllowOnceQueries.activeRejectionInTx(userId, videoKey, now)?.cooldownMs ?: 0L
            remaining to cooldownMs
        } ?: return FocusAllowOnceChallengeResult.NotEligible
        val (remainingToday, cooldownMs) = state
        if (remainingToday <= 0) return FocusAllowOnceChallengeResult.Exhausted

        val code = generateCaptchaCode()
        val challengeId = UUID.randomUUID().toString()
        val expiresAt = now + CHALLENGE_TTL_MS
        challenges.save(challengeId, FocusAllowOnceChallengePayload(userId, videoKey, code, expiresAt, kind.wireName), now)
        return FocusAllowOnceChallengeResult.Success(
            FocusAllowOnceChallengeResponse(
                challengeId = challengeId,
                challengeText = code,
                expiresAt = expiresAt,
                remainingToday = remainingToday,
                dailyLimit = kind.dailyLimit,
                kind = kind.wireName,
                cooldownMs = cooldownMs,
            ),
        )
    }

    suspend fun verifyChallenge(
        userId: String,
        challengeId: String,
        rawVideoUrl: String,
        rawCode: String,
        requestedTimezone: String? = null,
        rawKind: String = FocusAllowOnceKind.OUTSIDE_GROUP.wireName,
    ): FocusAllowOnceVerifyResult {
        val kind = FocusAllowOnceKind.fromWire(rawKind) ?: return FocusAllowOnceVerifyResult.InvalidKind
        val videoKey = FocusAllowOnceChallenges.normalizeVideoKey(rawVideoUrl) ?: return FocusAllowOnceVerifyResult.InvalidVideo
        val trimmedCode = rawCode.trim()
        if (challengeId.isBlank() || trimmedCode.isBlank()) return FocusAllowOnceVerifyResult.InvalidChallenge
        val now = clock()
        val payload = challenges.take(challengeId)
        val valid = payload != null &&
            payload.userId == userId &&
            payload.videoKey == videoKey &&
            payload.kind == kind.wireName &&
            now <= payload.expiresAt &&
            trimmedCode == payload.code
        if (!valid) return FocusAllowOnceVerifyResult.InvalidChallenge

        return DatabaseFactory.query {
            SubscriptionMutationLock.acquire(userId)
            val row = readSettledFocusRow(userId, now)
            if (!isActiveFocusRow(row, now) || !row.strictBlocking) return@query FocusAllowOnceVerifyResult.NotEligible
            val dayKey = FocusAllowOnceChallenges.currentDayKey(now, row.timezone, requestedTimezone)
            val usedToday = FocusAllowOnceQueries.countUsedInDayTx(userId, dayKey, kind)
            val existing = FocusAllowOnceQueries.activeOrPendingGrantInTx(userId, videoKey, kind, now)
            if (existing != null) {
                return@query FocusAllowOnceVerifyResult.Success(
                    FocusAllowOnceVerifyResponse(
                        remainingToday = (kind.dailyLimit - usedToday).coerceAtLeast(0),
                        dailyLimit = kind.dailyLimit,
                        expiresAt = existing.expiresAt,
                        kind = kind.wireName,
                        unlockAt = existing.unlockAt,
                    ),
                )
            }
            if (usedToday >= kind.dailyLimit) return@query FocusAllowOnceVerifyResult.Exhausted
            val cooldownMs = FocusAllowOnceQueries.activeRejectionInTx(userId, videoKey, now)?.cooldownMs ?: 0L
            val unlockAt = now + cooldownMs
            val expiresAt = unlockAt + ALLOW_ONCE_DURATION_MS
            FocusAllowOnceQueries.insertGrantInTx(
                userId = userId,
                videoKey = videoKey,
                dayKey = dayKey,
                kind = kind,
                source = FocusAllowOnceQueries.SOURCE_CAPTCHA,
                grantedAt = now,
                unlockAt = unlockAt,
                expiresAt = expiresAt,
            )
            FocusAllowOnceVerifyResult.Success(
                FocusAllowOnceVerifyResponse(
                    remainingToday = (kind.dailyLimit - (usedToday + 1)).coerceAtLeast(0),
                    dailyLimit = kind.dailyLimit,
                    expiresAt = expiresAt,
                    kind = kind.wireName,
                    unlockAt = unlockAt,
                ),
            )
        }
    }

    suspend fun cancelPendingGrant(
        userId: String,
        rawVideoUrl: String,
        requestedTimezone: String? = null,
    ): FocusAllowOnceCancelResult {
        val videoKey = FocusAllowOnceChallenges.normalizeVideoKey(rawVideoUrl) ?: return FocusAllowOnceCancelResult.InvalidVideo
        val now = clock()
        return DatabaseFactory.query {
            SubscriptionMutationLock.acquire(userId)
            val row = readSettledFocusRow(userId, now)
            val cancelledKind = FocusAllowOnceQueries.deletePendingCaptchaGrantsInTx(userId, videoKey, now)
                ?: return@query FocusAllowOnceCancelResult.NotFound
            val remaining = remainingTodayInTx(userId, now, row.timezone, requestedTimezone, cancelledKind)
            FocusAllowOnceCancelResult.Success(
                FocusAllowOnceCancelResponse(
                    kind = cancelledKind.wireName,
                    remainingToday = remaining,
                    dailyLimit = cancelledKind.dailyLimit,
                ),
            )
        }
    }

    companion object {
        const val DAILY_LIMIT = 3
        const val REWATCH_DAILY_LIMIT = 2
        const val ALLOW_ONCE_DURATION_MS = 2 * 60 * 60 * 1000L
        const val CHALLENGE_TTL_MS = 5 * 60 * 1000L
    }
}
