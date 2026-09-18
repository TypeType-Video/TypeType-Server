package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class OidcStartResponse(val authorizationUrl: String)
