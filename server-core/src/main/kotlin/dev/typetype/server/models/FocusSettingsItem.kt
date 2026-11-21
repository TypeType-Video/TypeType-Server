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
    val grayscaleThumbnails: Boolean = true,
    val startedAt: Long?,
    val disableAt: Long?,
    val entertainmentShare: Double,
    val active: Boolean = false,
    val focusAiEnabled: Boolean = false,
    val studyChannelKeys: List<String> = emptyList(),
)
