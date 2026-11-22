package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class FocusAllowOnceVerifyRequest(
    val challengeId: String,
    val videoUrl: String,
    val code: String,
    val timezone: String? = null,
)
