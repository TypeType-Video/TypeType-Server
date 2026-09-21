package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileItem(
    val id: String,
    val role: String?,
    val publicUsername: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val avatarType: String? = null,
    val avatarCode: String? = null,
)
