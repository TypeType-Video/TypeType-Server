package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceStartRequest(val deviceName: String? = null)

@Serializable
data class DeviceStartResponse(
    val deviceCode: String,
    val userCode: String,
    val expiresIn: Long,
    val interval: Long,
)

@Serializable
data class DeviceUserCodeRequest(val userCode: String)

@Serializable
data class DeviceLookupResponse(
    val userCode: String,
    val deviceName: String,
    val requestedAt: Long,
    val expiresAt: Long,
)

@Serializable
data class DeviceTokenRequest(val deviceCode: String)
