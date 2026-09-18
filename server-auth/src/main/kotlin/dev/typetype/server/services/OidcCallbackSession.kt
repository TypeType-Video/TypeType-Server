package dev.typetype.server.services

data class OidcCallbackSession(
    val accessToken: String,
    val refreshToken: String,
    val returnTo: String,
)
