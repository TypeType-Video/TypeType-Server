package dev.typetype.server.routes

import dev.typetype.server.services.SubscriptionSelection
import io.ktor.server.application.ApplicationCall

internal sealed interface SubscriptionSelectionParseResult {
    data class Valid(val selection: SubscriptionSelection) : SubscriptionSelectionParseResult
    data object Invalid : SubscriptionSelectionParseResult
}

internal fun ApplicationCall.parseSubscriptionSelection(): SubscriptionSelectionParseResult {
    val rawGroupId = request.queryParameters["groupId"]
    val groupId = rawGroupId?.takeIf(String::isNotBlank)
    if (rawGroupId != null && groupId == null) return SubscriptionSelectionParseResult.Invalid
    val rawUngrouped = request.queryParameters["ungrouped"]
    val ungrouped = when (rawUngrouped) {
        null -> false
        "true" -> true
        "false" -> false
        else -> return SubscriptionSelectionParseResult.Invalid
    }
    if (groupId != null && ungrouped) return SubscriptionSelectionParseResult.Invalid
    val selection = when {
        groupId != null -> SubscriptionSelection.Group(groupId)
        ungrouped -> SubscriptionSelection.Ungrouped
        else -> SubscriptionSelection.All
    }
    return SubscriptionSelectionParseResult.Valid(selection)
}
