package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class DeArrowTitleCandidate(
    val title: String,
    val original: Boolean,
    val votes: Int,
    val locked: Boolean,
    val uuid: String,
)
