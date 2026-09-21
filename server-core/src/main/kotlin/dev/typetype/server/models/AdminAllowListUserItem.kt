package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AdminAllowListUserItem(
    val id: String,
    val email: String,
    val name: String,
    val accessMode: String,
)
