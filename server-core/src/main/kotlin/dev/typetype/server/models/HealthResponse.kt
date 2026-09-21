package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String = "ok",
)
