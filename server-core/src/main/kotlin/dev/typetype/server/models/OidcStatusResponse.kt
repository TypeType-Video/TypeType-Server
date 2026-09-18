package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class OidcStatusResponse(
    val enabled: Boolean,
    val providerName: String? = null,
    val localLoginEnabled: Boolean,
    val autoRedirect: Boolean,
)
