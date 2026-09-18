package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SponsorBlockSegmentItem(
    val startTime: Double,
    val endTime: Double,
    val category: String,
    val action: String,
)
