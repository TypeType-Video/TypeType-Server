package dev.typetype.server.services

import dev.typetype.server.ai.AiCompletionResult
import dev.typetype.server.ai.AiKit
import dev.typetype.server.db.DatabaseFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

internal class FocusAiReviewService(
    private val clock: () -> Long = System::currentTimeMillis,
    private val aiKit: AiKit? = null,
    private val config: FocusAiConfig = FocusAiConfig(),
    private val studyChannels: FocusStudyChannelService,
    private val allowOnce: FocusAllowOnceService,
    private val snapshotCache: FocusVideoSnapshotCache,
    private val streamMetadataResolver: suspend (String) -> FocusVideoSnapshot? = { null },
    private val thumbnailFetcher: FocusAiThumbnailFetcher = FocusAiThumbnailFetcher(),
) {
    val enabled: Boolean
        get() = config.enabled && aiKit?.enabled == true

    suspend fun review(
        userId: String,
        rawVideoUrl: String,
        rawReason: String,
        requestedTimezone: String? = null,
    ): FocusAiReviewResult {
        val videoKey = FocusAllowOnceChallenges.normalizeVideoKey(rawVideoUrl)
            ?: return FocusAiReviewResult.InvalidVideo
        val reason = rawReason.trim()
        if (reason.isEmpty() || reason.length > MAX_REASON_INPUT_CHARS) return FocusAiReviewResult.InvalidReason
        val activeAi = aiKit?.takeIf { enabled } ?: return FocusAiReviewResult.Unavailable

        val snapshot = snapshotCache.get(videoKey)
            ?: streamMetadataResolver(rawVideoUrl)?.also { snapshotCache.put(videoKey, it) }
            ?: run {
                logger.warn("focus_ai_review event=unavailable reason=snapshot_missing videoKey={}", videoKey)
                return FocusAiReviewResult.Unavailable
            }

        val kind = when (val preCheck = preCheckInTx(userId, rawVideoUrl, videoKey, snapshot)) {
            is PreCheck.Early -> return preCheck.earlyResult
            is PreCheck.Proceed -> preCheck.kind
        }

        val thumbnail = thumbnailFetcher.fetch(snapshot.thumbnailUrl)
        val promptRequest = FocusAiPromptBuilder.build(kind, snapshot, reason, thumbnail)
        val aiResult = activeAi.complete(promptRequest)
        if (aiResult !is AiCompletionResult.Success) {
            logger.warn(
                "focus_ai_review event=unavailable videoKey={} kind={} reason={}",
                videoKey,
                kind.wireName,
                describeFailure(aiResult),
            )
            return FocusAiReviewResult.Unavailable
        }
        val score = parseScore(aiResult.text) ?: run {
            logger.warn(
                "focus_ai_review event=unavailable videoKey={} kind={} reason=score_parse_failed textLength={}",
                videoKey,
                kind.wireName,
                aiResult.text.length,
            )
            return FocusAiReviewResult.Unavailable
        }

        return recordDecisionInTx(
            userId = userId,
            videoKey = videoKey,
            kind = kind,
            score = score,
            requestedTimezone = requestedTimezone,
        )
    }

    private suspend fun preCheckInTx(
        userId: String,
        rawVideoUrl: String,
        videoKey: String,
        snapshot: FocusVideoSnapshot,
    ): PreCheck = DatabaseFactory.query {
        val now = clock()
        val loaded = FocusProfileLoader.loadInTx(userId, now, studyChannels, allowOnce)
            ?: return@query PreCheck.Early(FocusAiReviewResult.NotEligible)
        if (!loaded.row.strictBlocking) return@query PreCheck.Early(FocusAiReviewResult.NotEligible)

        val watched = FocusWatchedChecker.isVideoWatchedInTx(
            userId = userId,
            videoUrl = rawVideoUrl,
            streamDurationSeconds = snapshot.duration,
            isLive = snapshot.isLive,
        )
        val blockReason = loaded.profile.blockReasonForPlayback(
            url = rawVideoUrl,
            uploaderUrl = snapshot.uploaderUrl,
            publishedAt = snapshot.publishedAt,
            alreadyWatched = watched,
        ) ?: return@query PreCheck.Early(FocusAiReviewResult.NotNeeded)

        val kind = when (blockReason) {
            FocusBlockReason.OutsideStudy,
            is FocusBlockReason.StudyPending,
            -> FocusAllowOnceKind.OUTSIDE_GROUP
            FocusBlockReason.EntertainmentRewatch -> FocusAllowOnceKind.ENTERTAINMENT_REWATCH
            FocusBlockReason.Entertainment -> return@query PreCheck.Early(FocusAiReviewResult.NotEligible)
        }

        val rejection = FocusAllowOnceQueries.activeRejectionInTx(userId, videoKey, now)
        if (rejection != null) {
            return@query PreCheck.Early(
                FocusAiReviewResult.CooldownActive(rejection.cooldownMs, rejection.rejectionExpiresAt),
            )
        }
        PreCheck.Proceed(kind)
    }

    private suspend fun recordDecisionInTx(
        userId: String,
        videoKey: String,
        kind: FocusAllowOnceKind,
        score: Int,
        requestedTimezone: String?,
    ): FocusAiReviewResult = DatabaseFactory.query {
        SubscriptionMutationLock.acquire(userId)
        val now = clock()
        val row = readSettledFocusRow(userId, now)
        if (!isActiveFocusRow(row, now) || !row.strictBlocking) return@query FocusAiReviewResult.NotEligible

        if (FocusAiCooldownPolicy.isApproved(score, config.passScore)) {
            val expiresAt = allowOnce.grantAiApprovedInTx(userId, videoKey, row.timezone, requestedTimezone, kind, now)
            return@query FocusAiReviewResult.Approved(expiresAt)
        }

        val existingRejection = FocusAllowOnceQueries.activeRejectionInTx(userId, videoKey, now)
        if (existingRejection != null) {
            return@query FocusAiReviewResult.CooldownActive(
                existingRejection.cooldownMs,
                existingRejection.rejectionExpiresAt,
            )
        }
        val cooldownMs = FocusAiCooldownPolicy.cooldownMs(score, config.passScore, config.maxCooldownMinutes)
        val rejectionExpiresAt = now + config.rejectionCooldownMinutes.toLong() * MS_PER_MINUTE
        FocusAllowOnceQueries.insertAiRejectionInTx(
            userId = userId,
            videoKey = videoKey,
            kind = kind,
            score = score,
            cooldownMs = cooldownMs,
            reviewedAt = now,
            rejectionExpiresAt = rejectionExpiresAt,
        )
        FocusAiReviewResult.Rejected(cooldownMs, rejectionExpiresAt)
    }

    private fun parseScore(rawText: String): Int? {
        val cleaned = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val obj = runCatching { json.parseToJsonElement(cleaned).jsonObject }.getOrNull() ?: return null
        val numeric = obj["score"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        if (!numeric.isFinite()) return null
        return numeric.roundToInt().coerceIn(0, MAX_SCORE)
    }

    private fun describeFailure(result: AiCompletionResult): String = when (result) {
        is AiCompletionResult.Failed -> "failed:${result.reason}"
        is AiCompletionResult.RateLimited -> "rate_limited:${result.reason}"
        AiCompletionResult.Disabled -> "disabled"
        is AiCompletionResult.Success -> "success"
    }

    private sealed interface PreCheck {
        data class Proceed(val kind: FocusAllowOnceKind) : PreCheck
        data class Early(val earlyResult: FocusAiReviewResult) : PreCheck
    }

    companion object {
        private const val MAX_REASON_INPUT_CHARS = 1_000
        private const val MAX_SCORE = 100
        private const val MS_PER_MINUTE = 60_000L
        private val json = Json { ignoreUnknownKeys = true }
        private val logger = LoggerFactory.getLogger(FocusAiReviewService::class.java)
    }
}
