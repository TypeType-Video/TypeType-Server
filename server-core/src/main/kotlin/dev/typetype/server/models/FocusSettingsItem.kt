package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class FocusSettingsItem(
    val enabled: Boolean,
    val entertainmentGroupId: String?,
    val transitionDays: Int,
    val archiveDays: Int,
    val startedAt: Long?,
    val disableAt: Long?,
    val entertainmentShare: Double,
)
