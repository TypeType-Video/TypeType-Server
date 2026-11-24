package dev.typetype.server.services

import dev.typetype.server.ai.AiKit
import dev.typetype.server.cache.CacheService
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.models.FocusSettingsItem
import dev.typetype.server.models.FocusSettingsRequest
import dev.typetype.server.models.StreamResponse

class FocusService(
    private val clock: () -> Long = System::currentTimeMillis,
    cache: CacheService? = null,
    captchaCodeGenerator: () -> String = FocusCaptchaGenerator::generateCode,
    aiKit: AiKit? = null,
    focusAiConfig: FocusAiConfig = FocusAiConfig(),
    streamMetadataResolver: suspend (String) -> FocusVideoSnapshot? = { null },
    thumbnailFetcher: FocusAiThumbnailFetcher = FocusAiThumbnailFetcher(),
) {
    private val studyChannels = FocusStudyChannelService(clock)
    private val allowOnce = FocusAllowOnceService(clock, cache, captchaCodeGenerator)
    private val snapshotCache = FocusVideoSnapshotCache(cache)
    private val aiReview = FocusAiReviewService(
        clock = clock,
        aiKit = aiKit,
        config = focusAiConfig,
        studyChannels = studyChannels,
        allowOnce = allowOnce,
        snapshotCache = snapshotCache,
        streamMetadataResolver = streamMetadataResolver,
        thumbnailFetcher = thumbnailFetcher,
    )

    val aiReviewEnabled: Boolean
        get() = aiReview.enabled

    suspend fun get(userId: String, requestedTimezone: String? = null): FocusSettingsItem = DatabaseFactory.query {
        val now = clock()
        val row = readSettledFocusRow(userId, now)
        buildSettingsItem(userId, row, now, requestedTimezone)
    }

    suspend fun update(userId: String, request: FocusSettingsRequest): FocusUpdateResult = DatabaseFactory.query {
        val now = clock()
        val current = readSettledFocusRow(userId, now)
        val next = when {
            current.enabled && request.enabled -> current.copy(disableAt = null)
            current.enabled -> current.copy(disableAt = current.disableAt ?: (now + EXIT_DELAY_MS))
            else -> when (val change = applyRequest(userId, current, request)) {
                is FocusChange.Invalid -> return@query change.result
                is FocusChange.Valid -> change.row.copy(
                    enabled = request.enabled,
                    startedAt = if (request.enabled) now else current.startedAt,
                    disableAt = null,
                )
            }
        }
        writeFocusSettingsRow(userId, next, now)
        FocusUpdateResult.Success(buildSettingsItem(userId, next, now, request.timezone))
    }

    suspend fun profileFor(userId: String): FocusProfile? = DatabaseFactory.query {
        FocusProfileLoader.loadInTx(userId, clock(), studyChannels, allowOnce)?.profile
    }

    suspend fun playbackBlockContext(
        userId: String,
        videoUrl: String,
        stream: StreamResponse,
    ): FocusPlaybackBlockContext? = FocusPlaybackEvaluator.evaluateBlockContext(
        userId = userId,
        videoUrl = videoUrl,
        stream = stream,
        clock = clock,
        studyChannels = studyChannels,
        allowOnce = allowOnce,
        snapshotCache = snapshotCache,
        aiReviewEnabled = aiReview.enabled,
    )

    suspend fun createAllowOnceChallenge(
        userId: String,
        rawVideoUrl: String,
        requestedTimezone: String? = null,
        rawKind: String = FocusAllowOnceKind.OUTSIDE_GROUP.wireName,
    ): FocusAllowOnceChallengeResult = allowOnce.createChallenge(userId, rawVideoUrl, requestedTimezone, rawKind)

    suspend fun verifyAllowOnceChallenge(
        userId: String,
        challengeId: String,
        rawVideoUrl: String,
        rawCode: String,
        requestedTimezone: String? = null,
        rawKind: String = FocusAllowOnceKind.OUTSIDE_GROUP.wireName,
    ): FocusAllowOnceVerifyResult = allowOnce.verifyChallenge(userId, challengeId, rawVideoUrl, rawCode, requestedTimezone, rawKind)

    suspend fun cancelPendingAllowOnce(
        userId: String,
        rawVideoUrl: String,
        requestedTimezone: String? = null,
    ): FocusAllowOnceCancelResult = allowOnce.cancelPendingGrant(userId, rawVideoUrl, requestedTimezone)

    suspend fun reviewWithAi(
        userId: String,
        rawVideoUrl: String,
        rawReason: String,
        requestedTimezone: String? = null,
    ): FocusAiReviewResult = aiReview.review(userId, rawVideoUrl, rawReason, requestedTimezone)

    suspend fun addStudyChannel(
        userId: String,
        rawChannelUrl: String,
        rawName: String = "",
        rawAvatarUrl: String = "",
    ): FocusStudyChannelResult = studyChannels.addStudyChannel(userId, rawChannelUrl, rawName, rawAvatarUrl)

    suspend fun removeStudyChannel(userId: String, rawChannelUrl: String): FocusStudyChannelRemoveResult =
        studyChannels.removeStudyChannel(userId, rawChannelUrl)

    private fun buildSettingsItem(
        userId: String,
        row: FocusSettingsRow,
        now: Long,
        requestedTimezone: String?,
    ): FocusSettingsItem {
        val active = isActiveFocusRow(row, now)
        val studyKeys = if (active) activeStudyKeys(userId, row, now) else emptyList()
        val outsideRemaining = allowOnce.remainingTodayInTx(
            userId, now, row.timezone, requestedTimezone, FocusAllowOnceKind.OUTSIDE_GROUP,
        )
        val rewatchRemaining = allowOnce.remainingTodayInTx(
            userId, now, row.timezone, requestedTimezone, FocusAllowOnceKind.ENTERTAINMENT_REWATCH,
        )
        return row.toItem(
            now = now,
            active = active,
            studyChannelKeys = studyKeys,
            allowOnceRemainingToday = outsideRemaining,
            allowOnceDailyLimit = FocusAllowOnceService.DAILY_LIMIT,
            rewatchAllowOnceRemainingToday = rewatchRemaining,
            rewatchAllowOnceDailyLimit = FocusAllowOnceService.REWATCH_DAILY_LIMIT,
            aiReviewEnabled = aiReview.enabled,
        )
    }

    private fun activeStudyKeys(userId: String, row: FocusSettingsRow, now: Long): List<String> {
        val entertainment = row.entertainmentGroupId?.let { studyChannels.channelsOf(userId, it) }.orEmpty()
        return studyChannels.partitionStudyChannels(userId, row, now, entertainment).active.sorted()
    }

    private fun applyRequest(userId: String, current: FocusSettingsRow, request: FocusSettingsRequest): FocusChange {
        val transitionDays = request.transitionDays ?: current.transitionDays
        val archiveDays = request.archiveDays ?: current.archiveDays
        if (transitionDays !in DAY_RANGE || archiveDays !in DAY_RANGE) return FocusChange.Invalid(FocusUpdateResult.InvalidDays)
        val schedule = request.schedule ?: current.schedule
        val timezone = request.timezone?.takeIf { it.isNotBlank() } ?: current.timezone
        if (!FocusSchedule.isValid(schedule, timezone)) return FocusChange.Invalid(FocusUpdateResult.InvalidSchedule)
        val entertainment = request.entertainmentGroupId ?: current.entertainmentGroupId
        val study = when (val requested = request.studyGroupId) {
            null -> current.studyGroupId
            "" -> null
            else -> requested
        }
        val unknown = listOfNotNull(entertainment, study).any { !SubscriptionGroupQueries.exists(userId, it) }
        val conflicting = study != null && study == entertainment
        if (unknown || conflicting || (request.enabled && entertainment == null)) {
            return FocusChange.Invalid(FocusUpdateResult.InvalidGroup)
        }
        val row = current.copy(
            entertainmentGroupId = entertainment,
            studyGroupId = study,
            transitionDays = transitionDays,
            archiveDays = archiveDays,
            strictBlocking = request.strictBlocking ?: current.strictBlocking,
            grayscaleThumbnails = request.grayscaleThumbnails ?: current.grayscaleThumbnails,
            schedule = schedule,
            timezone = timezone,
        )
        return FocusChange.Valid(row)
    }

    private sealed interface FocusChange {
        data class Valid(val row: FocusSettingsRow) : FocusChange
        data class Invalid(val result: FocusUpdateResult) : FocusChange
    }

    companion object {
        const val EXIT_DELAY_MS = 24 * 60 * 60 * 1000L
        const val STUDY_ADD_DELAY_MS = FocusStudyChannelService.STUDY_ADD_DELAY_MS
        private val DAY_RANGE = 1..365
    }
}
