package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ChannelResultItem(
    val id: String,
    val name: String,
    val url: String,
    val thumbnailUrl: String,
    val description: String,
    val subscriberCount: Long,
    val streamCount: Long,
    val isVerified: Boolean,
)
