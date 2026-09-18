package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AccountProfileItem(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val isDefault: Boolean,
    val lastUsedAt: Long,
    val publicUsername: String? = null,
    val avatarUrl: String? = null,
    val avatarType: String? = null,
    val avatarCode: String? = null,
)
