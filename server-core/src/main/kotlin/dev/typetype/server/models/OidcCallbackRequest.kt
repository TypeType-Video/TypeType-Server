package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class OidcCallbackRequest(
    val code: String,
    val state: String,
    val redirectUri: String,
)
