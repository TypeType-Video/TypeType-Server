package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AdminManagedAccessUsersResponse(
    val items: List<AdminManagedAccessUserItem>,
    val nextpage: String?,
)
