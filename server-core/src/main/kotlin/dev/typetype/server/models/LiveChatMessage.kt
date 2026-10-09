package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class LiveChatMessage(
    val id: String,
    val text: String,
    val receivedAtMs: Long,
    val authorName: String? = null,
    val authorAvatarUrl: String? = null,
    val moderator: Boolean = false,
)
