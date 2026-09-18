package dev.typetype.server.services

import dev.typetype.server.models.SubscriptionGroupItem

sealed interface SubscriptionGroupWriteResult {
    data class Success(val group: SubscriptionGroupItem) : SubscriptionGroupWriteResult
    data object InvalidName : SubscriptionGroupWriteResult
    data object DuplicateName : SubscriptionGroupWriteResult
    data object NotFound : SubscriptionGroupWriteResult
}

sealed interface SubscriptionGroupMembershipResult {
    data object Success : SubscriptionGroupMembershipResult
    data object GroupNotFound : SubscriptionGroupMembershipResult
    data object SubscriptionNotFound : SubscriptionGroupMembershipResult
    data object MembershipNotFound : SubscriptionGroupMembershipResult
}
