package dev.typetype.server.services

import dev.typetype.server.models.FocusAllowOnceVerifyResponse

sealed interface FocusAllowOnceVerifyResult {
    data class Success(val response: FocusAllowOnceVerifyResponse) : FocusAllowOnceVerifyResult
    data object InvalidVideo : FocusAllowOnceVerifyResult
    data object InvalidChallenge : FocusAllowOnceVerifyResult
    data object Exhausted : FocusAllowOnceVerifyResult
    data object NotEligible : FocusAllowOnceVerifyResult
}
