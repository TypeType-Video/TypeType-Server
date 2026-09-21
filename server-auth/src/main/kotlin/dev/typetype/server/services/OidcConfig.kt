package dev.typetype.server.services

data class OidcConfig(
    val issuer: String,
    val clientId: String,
    val clientSecret: String,
    val discoveryUrl: String,
    val scopes: String,
    val providerName: String,
)
