package dev.typetype.server.services

internal data class LoadedFocusProfile(
    val row: FocusSettingsRow,
    val profile: FocusProfile,
)

internal object FocusProfileLoader {
    fun loadInTx(
        userId: String,
        now: Long,
        studyChannels: FocusStudyChannelService,
        allowOnce: FocusAllowOnceService,
    ): LoadedFocusProfile? {
        val row = readSettledFocusRow(userId, now)
        val entertainment = row.entertainmentGroupId
        if (!isActiveFocusRow(row, now) || entertainment == null) return null
        val entertainmentChannels = studyChannels.channelsOf(userId, entertainment)
        val partitionedStudy = studyChannels.partitionStudyChannels(userId, row, now, entertainmentChannels)
        if (
            !row.strictBlocking &&
            entertainmentChannels.isEmpty() &&
            partitionedStudy.active.isEmpty() &&
            partitionedStudy.pending.isEmpty()
        ) {
            return null
        }
        val temporaryAllowed = if (row.strictBlocking) allowOnce.activeVideoKeysInTx(userId, now) else emptySet()
        val profile = FocusProfile(
            seed = userId,
            entertainmentChannels = entertainmentChannels,
            studyChannels = partitionedStudy.active,
            pendingStudyUnlocksAt = partitionedStudy.pending,
            entertainmentShare = FocusProfile.share(row.startedAt, row.transitionDays, now),
            archiveDays = row.archiveDays,
            archiveCutoffMs = now - row.archiveDays * FocusProfile.DAY_MS,
            strictBlocking = row.strictBlocking,
            temporaryAllowedVideos = temporaryAllowed,
        )
        return LoadedFocusProfile(row, profile)
    }
}
