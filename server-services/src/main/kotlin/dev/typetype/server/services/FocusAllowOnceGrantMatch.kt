package dev.typetype.server.services

internal data class FocusAllowOnceGrantMatch(
    val kind: FocusAllowOnceKind,
    val unlockAt: Long,
    val expiresAt: Long,
)
