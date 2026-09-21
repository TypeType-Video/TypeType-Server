package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AdminUserItem(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
    val publicUsername: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val avatarType: String? = null,
    val avatarCode: String? = null,
    val suspended: Boolean,
    val verified: Boolean,
    val accessMode: String,
    val createdAt: Long,
)
