package dev.typetype.server.models

import dev.typetype.server.currentRequestId
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String,
    val code: String = "error",
    val requestId: String? = currentRequestId(),
)
