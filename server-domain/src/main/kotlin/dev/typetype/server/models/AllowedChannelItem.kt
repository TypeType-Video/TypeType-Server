package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AllowedChannelItem(
    val url: String,
    val name: String? = null,
    val thumbnailUrl: String? = null,
    val allowedAt: Long = 0L,
    val global: Boolean? = null,
)
