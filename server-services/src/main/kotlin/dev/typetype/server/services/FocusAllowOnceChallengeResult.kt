package dev.typetype.server.services

import dev.typetype.server.models.FocusAllowOnceChallengeResponse

sealed interface FocusAllowOnceChallengeResult {
    data class Success(val response: FocusAllowOnceChallengeResponse) : FocusAllowOnceChallengeResult
    data object InvalidVideo : FocusAllowOnceChallengeResult
    data object InvalidKind : FocusAllowOnceChallengeResult
    data object Exhausted : FocusAllowOnceChallengeResult
    data object NotEligible : FocusAllowOnceChallengeResult
}
