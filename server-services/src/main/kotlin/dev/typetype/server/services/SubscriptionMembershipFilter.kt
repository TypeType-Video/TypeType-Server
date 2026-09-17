package dev.typetype.server.services

data class SubscriptionMembershipFilter(
    val page: Int = 0,
    val limit: Int = 20,
    val search: String = "",
    val groupId: String? = null,
    val ungrouped: Boolean = false,
    val excluded: Boolean = false,
)
