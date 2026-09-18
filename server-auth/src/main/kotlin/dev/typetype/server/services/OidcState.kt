package dev.typetype.server.services

data class OidcState(
    val nonce: String,
    val redirectUri: String,
    val returnTo: String,
)
