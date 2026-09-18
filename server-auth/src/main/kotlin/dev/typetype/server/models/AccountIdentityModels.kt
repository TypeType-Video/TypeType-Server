package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AccountIdentityItem(
    val email: String,
    val name: String,
    val managedByOidc: Boolean,
)

@Serializable
data class AccountIdentityUpdateRequest(
    val email: String,
    val name: String,
    val currentPassword: String,
)

@Serializable
data class AdminIdentityUpdateRequest(
    val email: String,
    val name: String,
)
