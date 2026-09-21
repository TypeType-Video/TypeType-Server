package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class PublicProfileItem(
    val publicUsername: String,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val avatarType: String? = null,
    val avatarCode: String? = null,
)
