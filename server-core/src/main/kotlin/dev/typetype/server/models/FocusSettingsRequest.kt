package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class FocusSettingsRequest(
    val enabled: Boolean,
    val entertainmentGroupId: String? = null,
    val studyGroupId: String? = null,
    val schedule: String? = null,
    val timezone: String? = null,
    val transitionDays: Int? = null,
    val archiveDays: Int? = null,
    val strictBlocking: Boolean? = null,
    val grayscaleThumbnails: Boolean? = null,
)
