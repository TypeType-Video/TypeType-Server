package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.models.StreamResponse

internal object FocusPlaybackEvaluator {
    suspend fun evaluateBlockContext(
        userId: String,
        videoUrl: String,
        stream: StreamResponse,
        clock: () -> Long,
        studyChannels: FocusStudyChannelService,
        allowOnce: FocusAllowOnceService,
        snapshotCache: FocusVideoSnapshotCache,
        aiReviewEnabled: Boolean,
    ): FocusPlaybackBlockContext? {
        val snapshot = stream.toFocusVideoSnapshot()
        val context = DatabaseFactory.query {
            val now = clock()
            val loaded = FocusProfileLoader.loadInTx(userId, now, studyChannels, allowOnce)
                ?: return@query null
            if (!loaded.profile.strictBlocking) return@query null
            val watched = FocusWatchedChecker.isVideoWatchedInTx(
                userId = userId,
                videoUrl = videoUrl,
                streamDurationSeconds = stream.duration,
                isLive = snapshot.isLive,
            )
            val reason = loaded.profile.blockReasonForPlayback(
                url = videoUrl,
                uploaderUrl = stream.uploaderUrl,
                publishedAt = snapshot.publishedAt,
                alreadyWatched = watched,
            ) ?: return@query null

            val kind = when (reason) {
                FocusBlockReason.OutsideStudy,
                is FocusBlockReason.StudyPending,
                -> FocusAllowOnceKind.OUTSIDE_GROUP
                FocusBlockReason.EntertainmentRewatch -> FocusAllowOnceKind.ENTERTAINMENT_REWATCH
                FocusBlockReason.Entertainment -> null
            }
            val videoKey = FocusAllowOnceChallenges.normalizeVideoKey(videoUrl)
            val pending = if (kind != null && videoKey != null) {
                FocusAllowOnceQueries.pendingGrantInTx(userId, videoKey, now)
            } else {
                null
            }
            val rejection = if (kind != null && videoKey != null) {
                FocusAllowOnceQueries.activeRejectionInTx(userId, videoKey, now)
            } else {
                null
            }
            FocusPlaybackBlockContext(
                reason = reason,
                allowOnceKind = kind,
                aiReviewEligible = kind != null && aiReviewEnabled,
                aiRejected = kind != null && aiReviewEnabled && rejection != null,
                aiRejectionExpiresAt = if (aiReviewEnabled) rejection?.rejectionExpiresAt else null,
                allowOnceCooldownMs = if (aiReviewEnabled) rejection?.cooldownMs else null,
                allowOncePendingUnlockAt = pending?.unlockAt,
            )
        }
        if (context != null && context.allowOnceKind != null) {
            FocusAllowOnceChallenges.normalizeVideoKey(videoUrl)?.let { key ->
                snapshotCache.put(key, snapshot)
            }
        }
        return context
    }
}
