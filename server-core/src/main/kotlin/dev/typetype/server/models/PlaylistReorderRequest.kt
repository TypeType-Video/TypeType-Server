package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistReorderRequest(val order: List<String>)
