package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ChannelResponse(
    val name: String,
    val description: String,
    val avatarUrl: String,
    val bannerUrl: String,
    val subscriberCount: Long,
    val isVerified: Boolean,
    val videos: List<VideoItem>,
    val nextpage: String?,
)
