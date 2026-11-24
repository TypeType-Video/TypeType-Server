package dev.typetype.server.services

internal data class FocusAiRejectionRecord(
    val kind: FocusAllowOnceKind,
    val score: Int,
    val cooldownMs: Long,
    val rejectionExpiresAt: Long,
)
