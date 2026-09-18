package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class OidcPublicConfig(
    val enabled: Boolean,
    val providerName: String? = null,
)
