package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class InstanceMinClientVersion(
    val android: String? = null,
)
