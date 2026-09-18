package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class DeepHealthResponse(
    val status: String,
    val checks: Map<String, String>,
)
