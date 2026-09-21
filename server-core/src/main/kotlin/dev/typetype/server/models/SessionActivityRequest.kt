package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SessionActivityRequest(
    val clientName: String? = null,
    val clientVersion: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val deviceType: String? = null,
)
