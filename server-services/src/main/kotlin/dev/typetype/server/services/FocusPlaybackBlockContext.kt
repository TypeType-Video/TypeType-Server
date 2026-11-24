package dev.typetype.server.services

data class FocusPlaybackBlockContext(
    val reason: FocusBlockReason,
    val allowOnceKind: FocusAllowOnceKind? = null,
    val aiReviewEligible: Boolean = false,
    val aiRejected: Boolean = false,
    val aiRejectionExpiresAt: Long? = null,
    val allowOnceCooldownMs: Long? = null,
    val allowOncePendingUnlockAt: Long? = null,
)
