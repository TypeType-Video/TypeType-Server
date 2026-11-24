package dev.typetype.server.services

import dev.typetype.server.models.FocusAllowOnceCancelResponse

sealed interface FocusAllowOnceCancelResult {
    data class Success(val response: FocusAllowOnceCancelResponse) : FocusAllowOnceCancelResult
    data object InvalidVideo : FocusAllowOnceCancelResult
    data object NotFound : FocusAllowOnceCancelResult
}
