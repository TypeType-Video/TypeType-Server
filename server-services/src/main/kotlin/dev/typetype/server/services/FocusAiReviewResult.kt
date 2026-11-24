package dev.typetype.server.services

sealed interface FocusAiReviewResult {
    data class Approved(val expiresAt: Long) : FocusAiReviewResult
    data class Rejected(val cooldownMs: Long, val rejectionExpiresAt: Long) : FocusAiReviewResult
    data class CooldownActive(val cooldownMs: Long, val rejectionExpiresAt: Long) : FocusAiReviewResult
    data object Unavailable : FocusAiReviewResult
    data object InvalidVideo : FocusAiReviewResult
    data object InvalidReason : FocusAiReviewResult
    data object NotEligible : FocusAiReviewResult
    data object NotNeeded : FocusAiReviewResult
}
