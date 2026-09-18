package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AvatarCustomRequest(val imageUrl: String)
