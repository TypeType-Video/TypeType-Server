package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class BlockedItem(
    val url: String,
    val name: String? = null,
    val thumbnailUrl: String? = null,
    val blockedAt: Long = 0L,
    val global: Boolean? = null,
)
