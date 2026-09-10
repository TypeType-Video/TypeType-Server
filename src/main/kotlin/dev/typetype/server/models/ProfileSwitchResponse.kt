package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ProfileSwitchResponse(
    val accessToken: String,
    val profile: AccountProfileItem,
)
