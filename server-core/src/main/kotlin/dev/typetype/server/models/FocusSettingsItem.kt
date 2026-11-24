package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class FocusSettingsItem(
    val enabled: Boolean,
    val entertainmentGroupId: String?,
    val studyGroupId: String?,
    val schedule: String,
    val timezone: String?,
    val transitionDays: Int,
    val archiveDays: Int,
    val strictBlocking: Boolean = false,
    val grayscaleThumbnails: Boolean = false,
    val startedAt: Long?,
    val disableAt: Long?,
    val entertainmentShare: Double,
    val active: Boolean = false,
    val studyChannelKeys: List<String> = emptyList(),
    val allowOnceRemainingToday: Int = 3,
    val allowOnceDailyLimit: Int = 3,
)
