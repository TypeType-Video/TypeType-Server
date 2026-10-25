package dev.typetype.server.services

data class TokenDeviceStart(
    val userCode: String,
    val verificationUrl: String,
    val expiresAt: Long,
    val intervalSeconds: Int,
)
