package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AdminUsersPageItem(
    val items: List<AdminUserItem>,
    val page: Int,
    val limit: Int,
    val total: Long,
)
