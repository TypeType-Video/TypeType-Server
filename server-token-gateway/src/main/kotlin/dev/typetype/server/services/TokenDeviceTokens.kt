package dev.typetype.server.services

data class TokenDeviceTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long,
)
