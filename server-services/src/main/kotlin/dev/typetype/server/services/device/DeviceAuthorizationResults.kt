package dev.typetype.server.services.device

import dev.typetype.server.models.DeviceLookupResponse
import dev.typetype.server.models.DeviceStartResponse

sealed interface DeviceStartResult {
    data class Started(val response: DeviceStartResponse) : DeviceStartResult
    data object Disabled : DeviceStartResult
    data object RateLimited : DeviceStartResult
}

sealed interface DeviceDecisionResult {
    data class Found(val request: DeviceLookupResponse) : DeviceDecisionResult
    data object Approved : DeviceDecisionResult
    data object Denied : DeviceDecisionResult
    data object Disabled : DeviceDecisionResult
    data object GuestForbidden : DeviceDecisionResult
    data object Locked : DeviceDecisionResult
    data object NotFound : DeviceDecisionResult
    data object AlreadyDecided : DeviceDecisionResult
}

sealed interface DevicePollResult {
    data class Approved(val ownerUserId: String) : DevicePollResult
    data class Pending(val scanned: Boolean) : DevicePollResult
    data object SlowDown : DevicePollResult
    data object Expired : DevicePollResult
    data object Denied : DevicePollResult
    data object Disabled : DevicePollResult
    data object Invalid : DevicePollResult
}
