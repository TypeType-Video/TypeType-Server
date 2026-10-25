package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteStatusResponse(val isFavorite: Boolean)
