package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ProfileUpdateRequest(
    val publicUsername: String? = null,
    val bio: String? = null,
)
