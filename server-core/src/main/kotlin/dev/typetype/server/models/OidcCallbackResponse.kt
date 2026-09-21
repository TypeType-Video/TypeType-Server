package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class OidcCallbackResponse(
    val accessToken: String,
    val returnTo: String,
)
