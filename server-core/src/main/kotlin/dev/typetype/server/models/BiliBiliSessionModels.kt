package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class BiliBiliSessionStatusResponse(
    val status: String,
    val updatedAt: Long,
    val lastUsedAt: Long,
    val expiresAt: Long = 0,
)

@Serializable
data class BiliBiliQrLoginResponse(
    val qrUrl: String,
    val qrcodeKey: String,
    val expiresAt: Long,
)

@Serializable
data class BiliBiliQrPollRequest(
    val qrcodeKey: String,
)

@Serializable
data class BiliBiliHealthResponse(
    val status: String,
    val message: String = "",
)

@Serializable
data class BiliBiliQrPollResponse(
    val status: String,
    val message: String = "",
)
