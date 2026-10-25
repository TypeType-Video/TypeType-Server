package dev.typetype.server.services

data class TokenDevicePoll(
    val status: String,
    val tokens: TokenDeviceTokens?,
)
