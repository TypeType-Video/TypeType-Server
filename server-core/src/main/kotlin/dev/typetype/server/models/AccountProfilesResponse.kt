package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AccountProfilesResponse(
    val profiles: List<AccountProfileItem>,
    val activeProfileId: String,
    val defaultProfileId: String,
)
