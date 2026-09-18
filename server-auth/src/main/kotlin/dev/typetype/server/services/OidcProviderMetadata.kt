package dev.typetype.server.services

data class OidcProviderMetadata(
    val issuer: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val jwksUri: String,
)
