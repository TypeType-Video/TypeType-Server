package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class CustomAvatarItem(
    val avatarUrl: String,
    val mediaType: String,
    val size: Int,
)
