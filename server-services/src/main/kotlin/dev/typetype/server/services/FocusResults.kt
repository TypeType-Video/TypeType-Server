package dev.typetype.server.services

import dev.typetype.server.models.FocusSettingsItem
import dev.typetype.server.models.FocusStudyChannelResponse

sealed interface FocusUpdateResult {
    data class Success(val item: FocusSettingsItem) : FocusUpdateResult
    data object InvalidGroup : FocusUpdateResult
    data object InvalidDays : FocusUpdateResult
    data object InvalidSchedule : FocusUpdateResult
}

sealed interface FocusStudyChannelResult {
    data class Success(val response: FocusStudyChannelResponse) : FocusStudyChannelResult
    data object InvalidChannel : FocusStudyChannelResult
    data object NoStudyGroup : FocusStudyChannelResult
    data object EntertainmentConflict : FocusStudyChannelResult
}

sealed interface FocusStudyChannelRemoveResult {
    data object Success : FocusStudyChannelRemoveResult
    data object InvalidChannel : FocusStudyChannelRemoveResult
    data object NoStudyGroup : FocusStudyChannelRemoveResult
    data object NotFound : FocusStudyChannelRemoveResult
}
